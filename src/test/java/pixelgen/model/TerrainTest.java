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
    void lightFromTheTopRightPutsTheLipOnTheOtherSide() {
        Terrain t = terrain().withLight(Light.TOP_RIGHT);
        assertEquals(Grid.of("gyww", "gyww", "gyww", "gyww"), t.blend(List.of("w"), true));
        assertEquals(Grid.of("wkGg", "wkGg", "wkGg", "wkGg"), t.blend(List.of("e"), true));
        assertEquals(Grid.of("gggg", "GGGG", "kkkk", "wwww"), t.blend(List.of("n"), true), "top and bottom as before");
    }

    /** Every light gives the top-left result on a mirrored terrain, mirrored back. */
    @Test
    void everyLightMirrorsTheTopLeftShading() {
        Terrain t = new Terrain("t", Grid.of("gggg", "gggg", "gggg", "gggg"), Grid.of("wwww", "wwww", "wwww", "wwww"),
                'G', 'y', 'k', Map.of("n", new int[] {2, 1, 3, 2}, "s", new int[] {1, 2, 2, 1},
                "w", new int[] {2, 3, 1, 2}, "e", new int[] {1, 1, 2, 1}));
        List<List<String>> combos = List.of(List.of("n"), List.of("s"), List.of("w"), List.of("e"),
                List.of("n", "w"), List.of("n", "e"), List.of("s", "w"), List.of("s", "e"));
        for (Light light : Light.values()) {
            boolean fx = light.lx() > 0, fy = light.ly() > 0;
            Terrain mirrored = mirror(t, fx, fy);
            for (List<String> names : combos) {
                for (boolean union : new boolean[] {true, false}) {
                    Grid expected = mirrored.blend(names.stream().map(n -> mirrorName(n, fx, fy)).toList(), union);
                    if (fx) expected = expected.flipX();
                    if (fy) expected = expected.flipY();
                    assertEquals(expected, t.withLight(light).blend(names, union),
                            light.word() + " " + names + (union ? " |" : " &"));
                }
            }
        }
    }

    /** The terrain mirrored left-right ({@code fx}) and/or top-bottom ({@code fy}), lit from the top left. */
    private static Terrain mirror(Terrain t, boolean fx, boolean fy) {
        Map<String, int[]> profiles = new java.util.HashMap<>();
        t.profiles().forEach((name, d) -> {
            boolean perColumn = name.charAt(0) == 'n' || name.charAt(0) == 's';
            boolean reverse = perColumn ? fx : fy;
            int[] copy = d.clone();
            if (reverse) for (int i = 0; i < copy.length; i++) copy[i] = d[d.length - 1 - i];
            profiles.put(mirrorName(name, fx, fy), copy);
        });
        Grid raised = t.raised(), lower = t.lower();
        if (fx) { raised = raised.flipX(); lower = lower.flipX(); }
        if (fy) { raised = raised.flipY(); lower = lower.flipY(); }
        return new Terrain(t.name(), raised, lower, t.bank(), t.lip(), t.shadow(), profiles, Light.TOP_LEFT);
    }

    private static String mirrorName(String name, boolean fx, boolean fy) {
        char side = name.charAt(0);
        if (fx && (side == 'w' || side == 'e')) side = side == 'w' ? 'e' : 'w';
        if (fy && (side == 'n' || side == 's')) side = side == 'n' ? 's' : 'n';
        return side + name.substring(1);
    }

    @Test
    void noLightSymbolsMeansPlainTextures() {
        Terrain t = terrain();
        Terrain plain = new Terrain("p", t.raised(), t.lower(), (char) 0, (char) 0, (char) 0, t.profiles());
        assertEquals(Grid.of("gggg", "gggg", "wwww", "wwww"), plain.blend(List.of("n"), true));
    }
}
