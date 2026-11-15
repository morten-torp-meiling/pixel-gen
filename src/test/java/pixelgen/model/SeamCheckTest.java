package pixelgen.model;

import org.junit.jupiter.api.Test;
import pixelgen.parse.PaletteResolver;
import pixelgen.parse.Palettes;
import pixelgen.parse.TilesetParser;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SeamCheckTest {

    private static final PaletteResolver BASIC = n -> Palettes.load("basic", Path.of("src/test/resources"));

    private static Palette basic() throws Exception { return BASIC.load("basic"); }

    @Test
    void sampleTileset() throws Exception {
        Tileset t = TilesetParser.parseFile(Path.of("src/test/resources/tilesets/ground.tileset"), "g", BASIC);
        List<SeamCheck.Seam> seams = SeamCheck.check(t);
        // grass and grass_b both ways, edge only sideways (x), seam both ways: 7 seams.
        assertEquals(List.of("grass X", "grass Y", "grass_b X", "grass_b Y", "edge X", "seam X", "seam Y"),
                seams.stream().map(s -> s.tile() + " " + s.axis()).toList());
        List<SeamCheck.Seam> bad = seams.stream().filter(s -> !s.ok()).toList();
        assertEquals(1, bad.size(), bad.toString());
        SeamCheck.Seam s = bad.getFirst();
        assertEquals("seam", s.tile());
        assertEquals(SeamCheck.Axis.X, s.axis());
        assertEquals(List.of(0, 1, 2, 3), s.positions());
        assertTrue(s.describe().startsWith("the right edge doesn't continue into the left edge at rows 0-3 (seam "),
                s.describe());
    }

    @Test
    void theEdgeTileWouldFailUpAndDown() throws Exception {
        Grid edge = Grid.of("GGGG", "gggg", "gggg", "gggg");
        SeamCheck.Seam y = SeamCheck.check("edge", edge, basic(), SeamCheck.Axis.Y);
        assertFalse(y.ok());
        assertEquals(List.of(0, 1, 2, 3), y.positions());
        assertTrue(y.describe().startsWith("the bottom edge doesn't continue into the top edge at columns 0-3"));
    }

    @Test
    void flatTilesAndStraySpecksPass() throws Exception {
        Palette p = basic();
        assertTrue(SeamCheck.check("flat", Grid.of("gggg", "gggg", "gggg", "gggg"), p, SeamCheck.Axis.X).ok());
        // One dark speck on the right edge: noticeable up close, but no line.
        String g = "g".repeat(16);
        Grid speck = Grid.of(g, g, g, g, g, g, g, "g".repeat(15) + "G", g, g, g, g, g, g, g, g);
        assertTrue(SeamCheck.check("speck", speck, p, SeamCheck.Axis.X).ok());
        assertTrue(SeamCheck.check("one", Grid.of("g"), p, SeamCheck.Axis.X).ok());
    }

    @Test
    void transparencyCounts() throws Exception {
        Grid half = Grid.of("gg..", "gg..");
        SeamCheck.Seam s = SeamCheck.check("half", half, basic(), SeamCheck.Axis.X);
        assertFalse(s.ok(), "solid left edge meets empty right edge");
        assertEquals(SeamCheck.EMPTY_VS_SOLID, s.seam(), 1e-9);
    }

    @Test
    void ranges() {
        assertEquals("0-3, 7, 9-10", SeamCheck.ranges(List.of(0, 1, 2, 3, 7, 9, 10)));
        assertEquals("5", SeamCheck.ranges(List.of(5)));
    }
}
