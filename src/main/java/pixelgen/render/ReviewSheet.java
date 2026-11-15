package pixelgen.render;

import pixelgen.model.Grid;
import pixelgen.model.Palette;
import pixelgen.model.Sprite;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The image Claude reads to check its own work: every frame and part greatly enlarged, so single
 * pixels can be told apart and named by coordinate.
 * <ul>
 *   <li>transparent pixels show a checkerboard, one square per sprite pixel;</li>
 *   <li>a faint line separates pixels, and a stronger blue line falls every 8 pixels;</li>
 *   <li>coordinates are written along the top and left edge every 8 pixels;</li>
 *   <li>the anchor pixel is outlined in pink;</li>
 *   <li>a last row shows every frame at 1x and 2x, to judge how it reads at game size.</li>
 * </ul>
 */
public final class ReviewSheet {

    public static final int PAD = 16;
    public static final int GAP = 28;
    public static final int LABEL_H = 18;
    public static final int RULER = 20;
    public static final int TITLE_H = 30;
    public static final int SECTION_H = 22;
    public static final int MAX_WIDTH = 1800;

    public static final Color BACKGROUND = new Color(0x3a3a3a);
    public static final Color CHECK_LIGHT = new Color(0xe6e6e6);
    public static final Color CHECK_DARK = new Color(0xc8c8c8);
    public static final Color PIXEL_LINE = new Color(0, 0, 0, 40);
    public static final Color MAJOR_LINE = new Color(40, 110, 255, 150);
    public static final Color ANCHOR = new Color(0xff2a7a);
    /** Onion skin: the previous frame's solid pixels, where the current frame has none. */
    public static final Color ONION = new Color(255, 40, 60, 110);
    static final Color TEXT = new Color(0xf0f0f0);
    static final Color TEXT_DIM = new Color(0xa8a8a8);

    /** The sheet plus where each enlarged grid was drawn, for tests and tools. */
    public record Result(BufferedImage image, int scale, Map<String, Rectangle> frameAreas,
                         Map<String, Rectangle> partAreas) {}

    private ReviewSheet() {}

    /** A scale that makes the frame about 256 px on its longest side, between 4 and 24. */
    public static int autoScale(Sprite s) {
        return Math.max(4, Math.min(24, 256 / Math.max(s.width(), s.height())));
    }

    public static Result render(Sprite sprite) { return render(sprite, autoScale(sprite)); }

    public static Result render(Sprite sprite, int scale) {
        if (scale < 1 || scale > Renderer.MAX_SCALE) {
            throw new IllegalArgumentException("scale must be between 1 and " + Renderer.MAX_SCALE);
        }
        List<Item> frames = new ArrayList<>();
        sprite.frames().values().forEach(f -> frames.add(new Item(f.name(), f.grid(), true)));
        List<Item> parts = new ArrayList<>();
        sprite.parts().forEach((name, grid) -> parts.add(new Item(name, grid, false)));

        // ---- layout pass ----
        int y = PAD + TITLE_H;
        y = layoutSection(frames, scale, y);
        if (!parts.isEmpty()) y = layoutSection(parts, scale, y + SECTION_H);
        int actualTop = y + SECTION_H;
        int actualH = LABEL_H + sprite.height() * 2;
        int width = PAD * 2;
        for (Item it : frames) width = Math.max(width, it.x + it.cellWidth(scale) + PAD);
        for (Item it : parts) width = Math.max(width, it.x + it.cellWidth(scale) + PAD);
        int actualW = PAD + frames.size() * (sprite.width() * 3 + GAP) + PAD;
        width = Math.max(width, Math.min(actualW, MAX_WIDTH));
        width = Math.max(width, 560);  // room for the title line
        int height = actualTop + actualH + PAD;

        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        Map<String, Rectangle> frameAreas = new LinkedHashMap<>();
        Map<String, Rectangle> partAreas = new LinkedHashMap<>();
        try {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(BACKGROUND);
            g.fillRect(0, 0, width, height);

            g.setColor(TEXT);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
            g.drawString(sprite.name() + "  " + sprite.width() + "x" + sprite.height() + "  palette "
                    + sprite.palette().name() + "  scale " + scale + "x  anchor (" + sprite.anchorX() + ","
                    + sprite.anchorY() + ")", PAD, PAD + 16);

            for (Item it : frames) {
                Rectangle r = drawItem(g, it, sprite, scale, true);
                frameAreas.put(it.name, r);
            }
            if (!parts.isEmpty()) {
                sectionLabel(g, "parts", parts.getFirst().y - SECTION_H + 15);
                for (Item it : parts) partAreas.put(it.name, drawItem(g, it, sprite, scale, false));
            }

            sectionLabel(g, "actual size (1x and 2x)", actualTop - 6);
            int x = PAD;
            for (Item it : frames) {
                BufferedImage one = Renderer.render(it.grid, sprite.palette());
                g.drawImage(one, x, actualTop + LABEL_H, null);
                g.drawImage(Renderer.scale(one, 2), x + sprite.width() + 6, actualTop + LABEL_H, null);
                x += sprite.width() * 3 + GAP;
                if (x + sprite.width() * 3 > width) break;  // keep the row on the sheet
            }
        } finally {
            g.dispose();
        }
        return new Result(img, scale, frameAreas, partAreas);
    }

    // ---- layout ----

    private static final class Item {
        final String name;
        final Grid grid;
        final boolean frame;
        int x, y;  // top-left of the whole cell (label + rulers + grid)

        Item(String name, Grid grid, boolean frame) {
            this.name = name;
            this.grid = grid;
            this.frame = frame;
        }

        int cellWidth(int s) { return RULER + grid.width() * s; }

        int cellHeight(int s) { return LABEL_H + RULER + grid.height() * s; }
    }

    /** Places items left to right, wrapping at MAX_WIDTH. Returns the y below the section. */
    private static int layoutSection(List<Item> items, int scale, int top) {
        int x = PAD, y = top, rowH = 0;
        for (Item it : items) {
            if (x > PAD && x + it.cellWidth(scale) + PAD > MAX_WIDTH) {
                x = PAD;
                y += rowH + GAP;
                rowH = 0;
            }
            it.x = x;
            it.y = y;
            x += it.cellWidth(scale) + GAP;
            rowH = Math.max(rowH, it.cellHeight(scale));
        }
        return y + rowH;
    }

    // ---- drawing ----

    private static void sectionLabel(Graphics2D g, String text, int baseline) {
        g.setColor(TEXT_DIM);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        g.drawString(text, PAD, baseline);
    }

    /** Draws one enlarged grid with label, rulers and lines; returns the grid's area. */
    private static Rectangle drawItem(Graphics2D g, Item it, Sprite sprite, int s, boolean isFrame) {
        Grid grid = it.grid;
        Palette palette = sprite.palette();
        int gx = it.x + RULER, gy = it.y + LABEL_H + RULER;
        int w = grid.width() * s, h = grid.height() * s;

        g.setColor(TEXT);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        g.drawString(it.name + (isFrame ? "" : "  (" + grid.width() + "x" + grid.height() + ")"), it.x, it.y + 13);

        drawPixels(g, grid, palette, gx, gy, s, null);

        // Rulers: coordinates every 8 pixels (and the last one), centred on the pixel.
        g.setColor(TEXT_DIM);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        for (int i = 0; i < grid.width(); i += 8) g.drawString(String.valueOf(i), gx + i * s + 1, gy - 5);
        for (int i = 0; i < grid.height(); i += 8) g.drawString(String.valueOf(i), it.x, gy + i * s + 11);

        g.setColor(TEXT_DIM);
        g.drawRect(gx - 1, gy - 1, w + 1, h + 1);

        if (isFrame) drawAnchor(g, sprite, gx, gy, s);
        return new Rectangle(gx, gy, w, h);
    }

    /** Outlines the anchor pixel of a frame drawn at (gx, gy). */
    static void drawAnchor(Graphics2D g, Sprite sprite, int gx, int gy, int s) {
        int ax = gx + sprite.anchorX() * s, ay = gy + sprite.anchorY() * s;
        g.setColor(ANCHOR);
        g.setStroke(new BasicStroke(2));
        g.drawRect(ax + 1, ay + 1, s - 2, s - 2);
        g.setStroke(new BasicStroke(1));
    }

    /**
     * Draws a grid enlarged {@code s} times at (gx, gy): a checkerboard where it is empty or
     * see-through, faint pixel lines and a blue line every 8 pixels. With an {@code onion} grid,
     * its solid pixels show in {@link #ONION} wherever this grid has no solid pixel.
     */
    static void drawPixels(Graphics2D g, Grid grid, Palette palette, int gx, int gy, int s, Grid onion) {
        int w = grid.width() * s, h = grid.height() * s;
        for (int py = 0; py < grid.height(); py++) {
            for (int px = 0; px < grid.width(); px++) {
                char c = grid.at(px, py);
                Palette.Entry e = c == Palette.TRANSPARENT ? null : palette.get(c);
                if (e == null || e.translucent()) {  // checkerboard, with see-through colours drawn over it
                    g.setColor(((px + py) & 1) == 0 ? CHECK_LIGHT : CHECK_DARK);
                    g.fillRect(gx + px * s, gy + py * s, s, s);
                    if (onion != null && palette.isOpaque(onion.at(px, py))) {
                        g.setColor(ONION);
                        g.fillRect(gx + px * s, gy + py * s, s, s);
                    }
                }
                if (e != null) {
                    g.setColor(new Color(e.argb(), true));
                    g.fillRect(gx + px * s, gy + py * s, s, s);
                }
            }
        }
        if (s >= 6) {  // pixel lines only where they leave the pixels readable
            g.setColor(PIXEL_LINE);
            for (int i = 1; i < grid.width(); i++) if (i % 8 != 0) g.fillRect(gx + i * s, gy, 1, h);
            for (int i = 1; i < grid.height(); i++) if (i % 8 != 0) g.fillRect(gx, gy + i * s, w, 1);
        }
        g.setColor(MAJOR_LINE);
        for (int i = 8; i < grid.width(); i += 8) g.fillRect(gx + i * s, gy, 1, h);
        for (int i = 8; i < grid.height(); i += 8) g.fillRect(gx, gy + i * s, w, 1);
    }
}
