package pixelgen.render;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pixelgen.model.Grid;
import pixelgen.model.Palette;
import pixelgen.model.Sprite;
import pixelgen.parse.PaletteResolver;
import pixelgen.parse.SpriteParser;

import java.awt.image.BufferedImage;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class RendererTest {

    private static final Path RESOURCES = Path.of("src/test/resources");
    // Colours from src/test/resources/palettes/basic.pal, as opaque ARGB.
    private static final int BLACK = 0xFF000000, GREY = 0xFF555555, WHITE = 0xFFFFFFFF,
            DARK_GREEN = 0xFF1F4A1F, RED = 0xFFC02020;

    private static Sprite apple;

    @BeforeAll
    static void load() throws Exception {
        apple = SpriteParser.parseFile(RESOURCES.resolve("sprites/apple.sprite"), "apple.sprite",
                PaletteResolver.cached(RESOURCES));
    }

    private static BufferedImage idle(int scale) {
        return Renderer.render(apple.frame("idle").grid(), apple.palette(), scale);
    }

    @Test
    void appleHasExactPixelColours() {
        BufferedImage img = idle(1);
        assertEquals(16, img.getWidth());
        assertEquals(16, img.getHeight());
        assertEquals(BLACK, img.getRGB(7, 1), "stem outline");
        assertEquals(DARK_GREEN, img.getRGB(7, 2), "stem");
        assertEquals(WHITE, img.getRGB(5, 6), "highlight");
        assertEquals(RED, img.getRGB(8, 8), "body");
        assertEquals(GREY, img.getRGB(12, 10), "shade");
    }

    @Test
    void everyPixelMatchesItsGridSymbol() {
        BufferedImage img = idle(1);
        Grid g = apple.frame("idle").grid();
        for (int y = 0; y < g.height(); y++) {
            for (int x = 0; x < g.width(); x++) {
                char c = g.at(x, y);
                int expected = c == Palette.TRANSPARENT ? 0 : 0xFF000000 | apple.palette().get(c).rgb();
                assertEquals(expected, img.getRGB(x, y), "pixel (" + x + "," + y + ") '" + c + "'");
            }
        }
    }

    @Test
    void dotsAreFullyTransparentAndColoursFullyOpaque() {
        BufferedImage img = idle(1);
        assertEquals(0, img.getRGB(0, 0) >>> 24, "corner alpha");
        assertEquals(255, img.getRGB(7, 2) >>> 24, "stem alpha");
    }

    @Test
    void scalingRepeatsEachPixelAsASolidBlock() {
        BufferedImage small = idle(1), big = idle(8);
        assertEquals(128, big.getWidth());
        assertEquals(128, big.getHeight());
        for (int y = 0; y < big.getHeight(); y++) {
            for (int x = 0; x < big.getWidth(); x++) {
                assertEquals(small.getRGB(x / 8, y / 8), big.getRGB(x, y), "(" + x + "," + y + ")");
            }
        }
    }

    @Test
    void scaleOneReturnsTheSameImage() {
        BufferedImage img = idle(1);
        assertSame(img, Renderer.scale(img, 1));
    }

    @Test
    void rejectsBadScales() {
        BufferedImage img = idle(1);
        assertThrows(IllegalArgumentException.class, () -> Renderer.scale(img, 0));
        assertThrows(IllegalArgumentException.class, () -> Renderer.scale(img, Renderer.MAX_SCALE + 1));
        BufferedImage wide = new BufferedImage(200, 1, BufferedImage.TYPE_INT_ARGB);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> Renderer.scale(wide, 64));
        assertTrue(e.getMessage().contains("12800x64"), e.getMessage());
    }

    @Test
    void unknownSymbolIsAnError() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> Renderer.render(Grid.of("KQ"), apple.palette()));
        assertTrue(e.getMessage().contains("'Q' at (1,0)"), e.getMessage());
    }
}
