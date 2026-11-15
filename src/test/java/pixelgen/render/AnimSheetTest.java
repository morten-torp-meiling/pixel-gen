package pixelgen.render;

import org.junit.jupiter.api.Test;
import pixelgen.model.Grid;
import pixelgen.model.Sprite;
import pixelgen.parse.Palettes;
import pixelgen.parse.SpriteParser;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AnimSheetTest {

    // basic palette. 'a' has a pixel at (0,0), 'b' at (1,0); anchor (1,1).
    private static final String TEXT = "size 2 2\npalette basic\nanchor 1 1\nframe a\nK.\n..\nend\nframe b\n.w\n..\nend\n"
            + "anim walk fps 8 loop: a b a*2\nanim hit fps 8 once: b a\n";

    private static Sprite sprite() throws Exception {
        return SpriteParser.parse(TEXT, "t.sprite", "t", n -> Palettes.load("basic", Path.of("src/test/resources")));
    }

    @Test
    void oneCellPerStepInPlayOrder() throws Exception {
        AnimSheet.Result r = AnimSheet.render(sprite(), 10, false);
        assertEquals(List.of("walk", "hit"), List.copyOf(r.cells().keySet()));
        List<Rectangle> walk = r.cells().get("walk");
        assertEquals(3, walk.size());
        assertEquals(2, r.cells().get("hit").size());
        assertTrue(walk.get(1).x > walk.get(0).x, "left to right");
        assertTrue(r.cells().get("hit").getFirst().y > walk.getFirst().y, "one row per anim");
        // the third walk cell shows frame a: black at its (0,0) pixel
        Rectangle c = walk.get(2);
        assertEquals(0xFF000000, r.image().getRGB(c.x + 5, c.y + 5));
    }

    @Test
    void onionSkinShowsThePreviousStepWhereTheCurrentOneIsEmpty() throws Exception {
        Sprite s = sprite();
        BufferedImage plain = AnimSheet.render(s, 10, false).image();
        AnimSheet.Result onion = AnimSheet.render(s, 10, true);
        // walk step 2 (frame b) follows a: a's pixel (0,0) shows red-tinted, b's pixel (1,0) stays white.
        Rectangle c = onion.cells().get("walk").get(1);
        int ghost = onion.image().getRGB(c.x + 5, c.y + 5);
        assertTrue(((ghost >> 16) & 0xFF) > ((ghost >> 8) & 0xFF) + 60, "red tint: " + Integer.toHexString(ghost));
        assertNotEquals(plain.getRGB(c.x + 5, c.y + 5), ghost);
        assertEquals(0xFFFFFFFF, onion.image().getRGB(c.x + 15, c.y + 5));
    }

    @Test
    void theFirstStepOfALoopGhostsTheLastStepButAOnceAnimHasNone() throws Exception {
        Sprite s = sprite();
        assertEquals(s.frame("a").grid(), AnimSheet.previous(s, s.anim("walk"), 0));
        assertEquals(s.frame("b").grid(), AnimSheet.previous(s, s.anim("walk"), 2));
        assertNull(AnimSheet.previous(s, s.anim("hit"), 0));
        assertEquals(Grid.of(".w", ".."), AnimSheet.previous(s, s.anim("hit"), 1));
    }

    @Test
    void spritesWithoutAnimsAreRejected() throws Exception {
        Sprite s = SpriteParser.parse("size 1 1\npalette basic\nframe a\nK\nend\n", "t.sprite", "t",
                n -> Palettes.load("basic", Path.of("src/test/resources")));
        assertThrows(IllegalArgumentException.class, () -> AnimSheet.render(s, false));
    }
}
