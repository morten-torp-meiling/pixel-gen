package pixelgen.render;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pixelgen.model.Grid;
import pixelgen.model.Palette;
import pixelgen.model.Sprite;
import pixelgen.parse.PaletteResolver;
import pixelgen.parse.Palettes;
import pixelgen.parse.SpriteParser;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ReviewSheetTest {

    private static final Path RESOURCES = Path.of("src/test/resources");
    private static Sprite apple;

    @BeforeAll
    static void load() throws Exception {
        apple = SpriteParser.parseFile(RESOURCES.resolve("sprites/apple.sprite"), "apple.sprite",
                PaletteResolver.cached(RESOURCES));
    }

    private static Sprite sprite(String text) throws Exception {
        return SpriteParser.parse(text, "t.sprite", "t", n -> Palettes.load("basic", RESOURCES));
    }

    @Test
    void autoScaleAimsForAbout256Pixels() throws Exception {
        assertEquals(16, ReviewSheet.autoScale(apple));
        assertEquals(8, ReviewSheet.autoScale(sprite("size 16 32\npalette p\nframe a\n" + "K".repeat(16).concat("\n").repeat(32) + "end\n")));
        assertEquals(24, ReviewSheet.autoScale(sprite("size 4 4\npalette p\nframe a\nKKKK\nKKKK\nKKKK\nKKKK\nend\n")));
    }

    @Test
    void centreOfEveryEnlargedPixelShowsItsColourOrTheCheckerboard() {
        ReviewSheet.Result r = ReviewSheet.render(apple, 16);
        Rectangle area = r.frameAreas().get("idle");
        assertEquals(new Rectangle(area.x, area.y, 256, 256), area);
        Grid g = apple.frame("idle").grid();
        for (int y = 0; y < g.height(); y++) {
            for (int x = 0; x < g.width(); x++) {
                int argb = r.image().getRGB(area.x + x * 16 + 8, area.y + y * 16 + 8);
                char c = g.at(x, y);
                int expected = c == Palette.TRANSPARENT
                        ? (((x + y) & 1) == 0 ? ReviewSheet.CHECK_LIGHT : ReviewSheet.CHECK_DARK).getRGB()
                        : 0xFF000000 | apple.palette().get(c).rgb();
                assertEquals(expected, argb, "pixel (" + x + "," + y + ") '" + c + "'");
            }
        }
    }

    @Test
    void neighbouringTransparentPixelsAlternateCheckerColours() {
        ReviewSheet.Result r = ReviewSheet.render(apple, 16);
        Rectangle a = r.frameAreas().get("idle");
        assertNotEquals(r.image().getRGB(a.x + 8, a.y + 8), r.image().getRGB(a.x + 16 + 8, a.y + 8));
    }

    @Test
    void everyEighthPixelHasABlueLine() {
        ReviewSheet.Result r = ReviewSheet.render(apple, 16);
        Rectangle a = r.frameAreas().get("idle");
        for (int y = 0; y < 16; y++) {
            if (y == apple.anchorY()) continue;  // anchor (8,14) is outlined on top of the line
            int argb = r.image().getRGB(a.x + 8 * 16, a.y + y * 16 + 8);
            int red = (argb >> 16) & 0xFF, blue = argb & 0xFF;
            assertTrue(blue > red + 40, "line at x=8, row " + y + ": " + Integer.toHexString(argb));
        }
    }

    @Test
    void anchorPixelIsOutlined() {
        ReviewSheet.Result r = ReviewSheet.render(apple, 16);
        Rectangle a = r.frameAreas().get("idle");
        int left = a.x + apple.anchorX() * 16 + 1, middle = a.y + apple.anchorY() * 16 + 8;
        assertEquals(ReviewSheet.ANCHOR.getRGB(), r.image().getRGB(left, middle));
    }

    @Test
    void partsAreShownAtTheirOwnSize() {
        ReviewSheet.Result r = ReviewSheet.render(apple, 16);
        Rectangle leaf = r.partAreas().get("leaf");
        assertEquals(3 * 16, leaf.width);
        assertEquals(2 * 16, leaf.height);
        assertTrue(leaf.y > r.frameAreas().get("idle").y + 256, "parts come below the frames");
    }

    @Test
    void manyFramesWrapWithinTheMaximumWidth() throws Exception {
        StringBuilder text = new StringBuilder("size 16 16\npalette p\n");
        for (int i = 0; i < 12; i++) {
            text.append("frame f").append(i).append('\n').append(("K".repeat(16) + "\n").repeat(16)).append("end\n");
        }
        ReviewSheet.Result r = ReviewSheet.render(sprite(text.toString()), 16);
        assertTrue(r.image().getWidth() <= ReviewSheet.MAX_WIDTH, "width " + r.image().getWidth());
        assertTrue(r.frameAreas().get("f11").y > r.frameAreas().get("f0").y, "frames wrapped to a new row");
        for (Rectangle area : r.frameAreas().values()) {
            assertTrue(area.x + area.width <= r.image().getWidth() && area.y + area.height <= r.image().getHeight());
        }
    }

    @Test
    void actualSizeRowShowsTheFrameAtOneToOne() {
        BufferedImage img = ReviewSheet.render(apple, 16).image();
        // The 1x copy is the bottom-left 16x16 block above the bottom padding.
        int top = img.getHeight() - ReviewSheet.PAD - 32;
        assertEquals(0xFF000000 | apple.palette().get('w').rgb(),
                img.getRGB(ReviewSheet.PAD + 5, top + 6), "highlight pixel (5,6) at 1x");
    }
}
