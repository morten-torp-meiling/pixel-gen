package pixelgen.render;

import pixelgen.model.Tileset;
import pixelgen.parse.MockupParser;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

import static pixelgen.render.ReviewSheet.BACKGROUND;
import static pixelgen.render.ReviewSheet.GAP;
import static pixelgen.render.ReviewSheet.LABEL_H;
import static pixelgen.render.ReviewSheet.PAD;
import static pixelgen.render.ReviewSheet.TEXT;
import static pixelgen.render.ReviewSheet.TEXT_DIM;
import static pixelgen.render.ReviewSheet.TITLE_H;

/**
 * A mock map: tiles placed as a {@code .mockup} file says, enlarged with no lines between them
 * (so seams show exactly as in the game), and again at 1× and 2× to judge it at game size.
 */
public final class MockupSheet {

    public static final int DEFAULT_SCALE = 4;
    /** Optional tile grid, faint enough not to hide a seam. */
    static final Color GRID = new Color(255, 255, 255, 50);

    private MockupSheet() {}

    /** The image, and where the enlarged map was drawn. */
    public record Result(BufferedImage image, Rectangle map) {}

    public static Result render(Tileset tileset, MockupParser.Mockup mock, String title, int scale, boolean grid) {
        int tw = tileset.tileWidth(), th = tileset.tileHeight();
        int mapW = mock.width() * tw, mapH = mock.height() * th;
        Rectangle map = new Rectangle(PAD, PAD + TITLE_H, mapW * scale, mapH * scale);
        int smallY = map.y + map.height + GAP + LABEL_H;
        int width = Math.max(map.x + map.width, PAD + mapW * 3 + GAP) + PAD;
        int height = smallY + mapH * 2 + PAD;

        BufferedImage img = new BufferedImage(Math.max(width, 420), height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(BACKGROUND);
            g.fillRect(0, 0, img.getWidth(), img.getHeight());
            g.setColor(TEXT);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
            g.drawString(title + "  " + mock.width() + "x" + mock.height() + " tiles  scale " + scale + "x", PAD, PAD + 16);

            drawMap(g, tileset, mock, map.x, map.y, scale);
            if (grid) {
                g.setColor(GRID);
                for (int x = 1; x < mock.width(); x++) g.fillRect(map.x + x * tw * scale, map.y, 1, map.height);
                for (int y = 1; y < mock.height(); y++) g.fillRect(map.x, map.y + y * th * scale, map.width, 1);
            }

            g.setColor(TEXT_DIM);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            g.drawString("actual size (1x and 2x)", PAD, smallY - 6);
            drawMap(g, tileset, mock, PAD, smallY, 1);
            drawMap(g, tileset, mock, PAD + mapW + GAP, smallY, 2);
        } finally {
            g.dispose();
        }
        return new Result(img, map);
    }

    private static void drawMap(Graphics2D g, Tileset tileset, MockupParser.Mockup mock, int x0, int y0, int s) {
        int tw = tileset.tileWidth(), th = tileset.tileHeight();
        for (int y = 0; y < mock.height(); y++) {
            for (int x = 0; x < mock.width(); x++) {
                String tile = mock.tile(x, y);
                int px = x0 + x * tw * s, py = y0 + y * th * s;
                if (tile == null) {
                    g.setColor(BACKGROUND.darker());
                    g.fillRect(px, py, tw * s, th * s);
                } else {
                    ReviewSheet.drawPixels(g, tileset.sprite().frame(tile).grid(), tileset.sprite().palette(),
                            px, py, s, null, false);
                }
            }
        }
    }
}
