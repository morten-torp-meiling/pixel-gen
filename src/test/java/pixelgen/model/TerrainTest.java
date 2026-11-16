package pixelgen.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TerrainTest {

    /** 4x4: raised 'g', lower 'w', bank 'G', lip 'y', shadow 'k'; every profile 2 deep. */
    private static Terrain terrain() {
        int[] two = {2, 2, 2, 2};
        return new Terrain("t", Grid.of("gggg", "gggg", "gggg", "gggg"), Grid.of("wwww", "wwww", "wwww", "wwww"),
                'G', 'y', 'k', Map.of("n", two, "s", two, "w", two, "e", two, "n2", new int[] {2, 1, 3, 2}));
    }

    @Test
    void raisedGroundAboveCastsShadowDown() {
        // Raised on top: its bottom row faces away from the light (bank), the row below is in its shade.
        assertEquals(Grid.of("gggg", "GGGG", "kkkk", "wwww"), terrain().blend(List.of("n"), true));
    }

    @Test
    void raisedGroundBelowCatchesTheLight() {
        assertEquals(Grid.of("wwww", "wwww", "yyyy", "gggg"), terrain().blend(List.of("s"), true));
    }

    @Test
    void raisedGroundOnTheLeftOrRight() {
        assertEquals(Grid.of("gGkw", "gGkw", "gGkw", "gGkw"), terrain().blend(List.of("w"), true));
        assertEquals(Grid.of("wwyg", "wwyg", "wwyg", "wwyg"), terrain().blend(List.of("e"), true));
    }

    @Test
    void outerCornersCombineInnerCornersOverlap() {
        assertEquals(Grid.of("gggg", "ggGG", "gGkk", "gGkw"), terrain().blend(List.of("n", "w"), true));
        assertEquals(Grid.of("gGkw", "GGkw", "kkww", "wwww"), terrain().blend(List.of("n", "w"), false));
    }

    @Test
    void theBoundaryFollowsTheProfile() {
        assertEquals(Grid.of("gGgg", "GkyG", "kwGk", "wwkw"), terrain().blend(List.of("n2"), true));
    }

    @Test
    void noLightSymbolsMeansPlainTextures() {
        Terrain t = terrain();
        Terrain plain = new Terrain("p", t.raised(), t.lower(), (char) 0, (char) 0, (char) 0, t.profiles());
        assertEquals(Grid.of("gggg", "gggg", "wwww", "wwww"), plain.blend(List.of("n"), true));
    }
}
