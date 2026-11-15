package pixelgen.render;

import pixelgen.model.Colors;
import pixelgen.model.Palette;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Draws a palette as one row per ramp on a neutral grey background. Each colour is a square
 * showing its grid symbol, with the hex value underneath. It is a review image, not game art.
 */
public final class PaletteSwatch {

    public static final int CELL = 48;
    public static final int GAP = 6;
    public static final int MARGIN = 16;
    public static final int LABEL_WIDTH = 150;
    public static final int TITLE_HEIGHT = 34;
    public static final int ROW_HEIGHT = CELL + 22;
    public static final Color BACKGROUND = new Color(0x80, 0x80, 0x80);

    private PaletteSwatch() {}

    /** Top-left corner of a colour's square, so tests and tools can find it. */
    public static int cellX(int indexInRamp) { return MARGIN + LABEL_WIDTH + indexInRamp * (CELL + GAP); }
    public static int cellY(int rampIndex) { return MARGIN + TITLE_HEIGHT + rampIndex * ROW_HEIGHT; }

    public static BufferedImage render(Palette palette) {
        int maxRamp = palette.ramps().stream().mapToInt(r -> r.entries().size()).max().orElse(1);
        int width = cellX(maxRamp) - GAP + MARGIN;
        int height = cellY(palette.ramps().size()) + MARGIN - (ROW_HEIGHT - CELL - 18);
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(BACKGROUND);
            g.fillRect(0, 0, width, height);

            g.setColor(Color.BLACK);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
            g.drawString(palette.name() + "  (" + palette.size() + " colours)", MARGIN, MARGIN + 18);

            Font label = new Font(Font.SANS_SERIF, Font.BOLD, 14);
            Font symbol = new Font(Font.MONOSPACED, Font.BOLD, 22);
            Font hex = new Font(Font.MONOSPACED, Font.PLAIN, 11);
            for (int r = 0; r < palette.ramps().size(); r++) {
                Palette.Ramp ramp = palette.ramps().get(r);
                int y = cellY(r);
                g.setColor(Color.BLACK);
                g.setFont(label);
                g.drawString(ramp.name() + (ramp.ordered() ? "" : " *"), MARGIN, y + CELL / 2 + 5);
                for (int i = 0; i < ramp.entries().size(); i++) {
                    Palette.Entry e = ramp.entries().get(i);
                    int x = cellX(i);
                    g.setColor(new Color(e.rgb()));
                    g.fillRect(x, y, CELL, CELL);
                    g.setColor(Colors.lightness(e.rgb()) < 0.6 ? Color.WHITE : Color.BLACK);
                    g.setFont(symbol);
                    centre(g, String.valueOf(e.symbol()), x, y + CELL / 2 + 8);
                    g.setColor(Color.BLACK);
                    g.setFont(hex);
                    centre(g, Colors.toHex(e.rgb()), x, y + CELL + 13);
                }
            }
        } finally {
            g.dispose();
        }
        return img;
    }

    private static void centre(Graphics2D g, String text, int cellX, int baseline) {
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, cellX + (CELL - fm.stringWidth(text)) / 2, baseline);
    }
}
