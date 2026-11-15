package pixelgen.render;

import org.junit.jupiter.api.Test;
import pixelgen.model.Sprite;
import pixelgen.parse.Palettes;
import pixelgen.parse.SpriteParser;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SheetPackerTest {

    // fx palette (K black, w white, G/g greens, ~ 33% black). Four 2x2 frames; 'd' is in no anim.
    private static final String TEXT = "size 2 2\npalette fx\n"
            + "frame a\nKw\nGg\nend\nframe b\n.w\n..\nend\nframe c\n~.\n..\nend\nframe d\ngg\ngg\nend\n"
            + "anim walk fps 8 loop: a b a\nanim idle fps 2 loop: a c\n";

    private static Sprite sprite(String text) throws Exception {
        return SpriteParser.parse(text, "t.sprite", "t", n -> Palettes.load("fx", Path.of("src/test/resources")));
    }

    @Test
    void oneRowPerAnimEachFrameStoredOnceAndUnusedFramesLast() throws Exception {
        SheetPacker.Layout l = SheetPacker.layout(sprite(TEXT), 1, 0);
        assertEquals(List.of(
                new SheetPacker.Row("walk", List.of("a", "b")),
                new SheetPacker.Row("idle", List.of("c")),
                new SheetPacker.Row(null, List.of("d"))), l.rows());
        assertEquals(new Rectangle(1, 1, 2, 2), l.frames().get("a"));
        assertEquals(new Rectangle(4, 1, 2, 2), l.frames().get("b"));
        assertEquals(new Rectangle(1, 4, 2, 2), l.frames().get("c"));
        assertEquals(new Rectangle(1, 7, 2, 2), l.frames().get("d"));
        assertEquals(7, l.width());
        assertEquals(10, l.height());
    }

    @Test
    void withoutPaddingCellsTouch() throws Exception {
        SheetPacker.Layout l = SheetPacker.layout(sprite(TEXT), 0, 0);
        assertEquals(new Rectangle(2, 0, 2, 2), l.frames().get("b"));
        assertEquals(4, l.width());
        assertEquals(6, l.height());
    }

    @Test
    void spritesWithoutAnimsGetOneRowOfAllFrames() throws Exception {
        SheetPacker.Layout l = SheetPacker.layout(sprite("size 1 1\npalette fx\nframe x\nK\nend\nframe y\nw\nend\n"), 1, 0);
        assertEquals(List.of(new SheetPacker.Row(null, List.of("x", "y"))), l.rows());
    }

    @Test
    void pixelsLandInTheirRegionsWithExactColoursAndAlpha() throws Exception {
        Sprite s = sprite(TEXT);
        SheetPacker.Layout l = SheetPacker.layout(s, 1, 0);
        BufferedImage img = SheetPacker.render(s, l);
        assertEquals(0xFF000000, img.getRGB(1, 1), "a (0,0) K");
        assertEquals(0xFFFFFFFF, img.getRGB(2, 1), "a (1,0) w");
        assertEquals(0, img.getRGB(0, 0) >>> 24, "padding is transparent");
        assertEquals(0, img.getRGB(3, 1) >>> 24, "gap between a and b is transparent");
        assertEquals(0, img.getRGB(4, 1) >>> 24, "b (0,0) is empty");
        assertEquals(0x55000000, img.getRGB(1, 4), "c (0,0) keeps the shadow's alpha");
    }

    @Test
    void extrudeRepeatsEdgePixelsIntoThePadding() throws Exception {
        Sprite s = sprite(TEXT);
        SheetPacker.Layout l = SheetPacker.layout(s, 2, 1);
        assertEquals(new Rectangle(2, 2, 2, 2), l.frames().get("a"));
        assertEquals(new Rectangle(6, 2, 2, 2), l.frames().get("b"));
        BufferedImage img = SheetPacker.render(s, l);
        int black = 0xFF000000, white = 0xFFFFFFFF;
        assertEquals(black, img.getRGB(1, 1), "corner copies a's top-left");
        assertEquals(black, img.getRGB(2, 1), "above a's top-left");
        assertEquals(white, img.getRGB(4, 2), "right of a's top-right");
        assertEquals(0, img.getRGB(5, 2) >>> 24, "b's own extrusion: its empty (0,0)");
        assertEquals(0, img.getRGB(0, 0) >>> 24, "outer padding beyond the extrusion stays empty");
    }

    @Test
    void badPaddingOrExtrude() throws Exception {
        Sprite s = sprite(TEXT);
        assertThrows(IllegalArgumentException.class, () -> SheetPacker.layout(s, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> SheetPacker.layout(s, 17, 0));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> SheetPacker.layout(s, 1, 1));
        assertTrue(e.getMessage().contains("half the padding"), e.getMessage());
    }
}
