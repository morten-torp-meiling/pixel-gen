package pixelgen.render;

import pixelgen.model.SeamCheck;
import pixelgen.model.Tileset;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static pixelgen.render.ReviewSheet.BACKGROUND;
import static pixelgen.render.ReviewSheet.GAP;
import static pixelgen.render.ReviewSheet.LABEL_H;
import static pixelgen.render.ReviewSheet.MAX_WIDTH;
import static pixelgen.render.ReviewSheet.PAD;
import static pixelgen.render.ReviewSheet.TEXT;
import static pixelgen.render.ReviewSheet.TEXT_DIM;
import static pixelgen.render.ReviewSheet.TITLE_H;

/**
 * Every tile painted {@value #REPEATS} times in each direction it wraps, as it will look on the
 * map. Ticks outside each block mark where the copies meet: grey for a seam that passes, red for
 * one that fails, with red marks beside the rows (or columns) that don't match.
 */
public final class TileCheckSheet {

    public static final int REPEATS = 4;
    public static final int DEFAULT_SCALE = 4;
    static final Color FAIL = new Color(0xff3b3b);
    static final int TICK = 6;

    private TileCheckSheet() {}

    /** The image plus each tile's block (all its copies). */
    public record Result(BufferedImage image, Map<String, Rectangle> blocks) {}

    public static Result render(Tileset tileset, List<SeamCheck.Seam> seams, int scale) {
        int tw = tileset.tileWidth() * scale, th = tileset.tileHeight() * scale;
        Font labelFont = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
        java.awt.FontMetrics fm = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics().getFontMetrics(labelFont);
        Map<String, Rectangle> blocks = new LinkedHashMap<>();
        int x = PAD + TICK, y = PAD + TITLE_H + LABEL_H + TICK, rowH = 0, width = 480;
        for (String tile : tileset.tiles()) {
            Tileset.Wrap w = tileset.wrap(tile);
            int bw = tw * (w.x() ? REPEATS : 1), bh = th * (w.y() ? REPEATS : 1);
            // The label starts at the left tick, so a cell is as wide as the block or its label.
            int cellW = Math.max(bw + TICK, fm.stringWidth(label(tileset, tile, seams)) - TICK);
            if (x > PAD + TICK && x + cellW + PAD > MAX_WIDTH) {
                x = PAD + TICK;
                y += rowH + 2 * TICK + GAP + LABEL_H;
                rowH = 0;
            }
            blocks.put(tile, new Rectangle(x, y, bw, bh));
            width = Math.max(width, x + cellW + PAD);
            rowH = Math.max(rowH, bh);
            x += cellW + TICK + GAP;
        }
        int height = y + rowH + TICK + PAD;

        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(BACKGROUND);
            g.fillRect(0, 0, width, height);
            g.setColor(TEXT);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
            g.drawString(tileset.name() + "  tile check, each tile repeated  scale " + scale + "x", PAD, PAD + 16);

            for (String tile : tileset.tiles()) {
                Rectangle b = blocks.get(tile);
                Tileset.Wrap w = tileset.wrap(tile);
                SeamCheck.Seam sx = find(seams, tile, SeamCheck.Axis.X), sy = find(seams, tile, SeamCheck.Axis.Y);
                boolean failed = (sx != null && !sx.ok()) || (sy != null && !sy.ok());
                g.setFont(labelFont);
                g.setColor(failed ? FAIL : TEXT_DIM);
                g.drawString(label(tileset, tile, seams), b.x - TICK, b.y - TICK - 5);

                int cols = b.width / tw, rows = b.height / th;
                for (int r = 0; r < rows; r++) {
                    for (int c = 0; c < cols; c++) {
                        ReviewSheet.drawPixels(g, tileset.sprite().frame(tile).grid(), tileset.sprite().palette(),
                                b.x + c * tw, b.y + r * th, scale, null, false);
                    }
                }
                if (sx != null) {
                    g.setColor(sx.ok() ? TEXT_DIM : FAIL);
                    for (int c = 1; c < cols; c++) {
                        int sxPos = b.x + c * tw;
                        g.fillRect(sxPos - 1, b.y - TICK, 2, TICK - 1);
                        g.fillRect(sxPos - 1, b.y + b.height + 1, 2, TICK - 1);
                    }
                    for (int row : sx.positions()) {  // which rows don't match, left of the block
                        for (int r = 0; r < rows; r++) g.fillRect(b.x - TICK, b.y + r * th + row * scale, TICK - 1, scale);
                    }
                }
                if (sy != null) {
                    g.setColor(sy.ok() ? TEXT_DIM : FAIL);
                    for (int r = 1; r < rows; r++) {
                        int syPos = b.y + r * th;
                        g.fillRect(b.x - TICK, syPos - 1, TICK - 1, 2);
                        g.fillRect(b.x + b.width + 1, syPos - 1, TICK - 1, 2);
                    }
                    for (int col : sy.positions()) {  // which columns don't match, above the block
                        for (int c = 0; c < cols; c++) g.fillRect(b.x + c * tw + col * scale, b.y - TICK, scale, TICK - 1);
                    }
                }
            }
        } finally {
            g.dispose();
        }
        return new Result(img, blocks);
    }

    private static String label(Tileset tileset, String tile, List<SeamCheck.Seam> seams) {
        Tileset.Wrap w = tileset.wrap(tile);
        boolean failed = seams.stream().anyMatch(s -> s.tile().equals(tile) && !s.ok());
        String status = w == Tileset.Wrap.NONE ? "not repeated (wrap none)"
                : failed ? "SEAM" : "ok" + (w == Tileset.Wrap.BOTH ? "" : " (wrap " + w.word() + ")");
        return tile + "  " + status;
    }

    private static SeamCheck.Seam find(List<SeamCheck.Seam> seams, String tile, SeamCheck.Axis axis) {
        for (SeamCheck.Seam s : seams) if (s.tile().equals(tile) && s.axis() == axis) return s;
        return null;
    }
}
