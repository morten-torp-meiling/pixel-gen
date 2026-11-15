package pixelgen.cli;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pixelgen.model.Tileset;
import pixelgen.parse.MockupParser;
import pixelgen.parse.Palettes;
import pixelgen.parse.ParseException;
import pixelgen.parse.TilesetParser;
import pixelgen.render.MockupSheet;

import javax.imageio.ImageIO;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** The .mockup format, the mock map image and the mockup command. */
class MockupCommandTest {

    @TempDir Path project;

    private final ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errBuf = new ByteArrayOutputStream();

    /** The sample tileset (basic palette, 4x4 tiles: grass, grass_b, edge, seam). */
    private static Tileset ground() throws Exception {
        return TilesetParser.parseFile(Path.of("src/test/resources/tilesets/ground.tileset"), "ground.tileset",
                n -> Palettes.load("basic", Path.of("src/test/resources")));
    }

    @BeforeEach
    void setUp() throws Exception {
        Files.createDirectories(project.resolve("palettes"));
        Files.copy(Path.of("src/test/resources/palettes/basic.pal"), project.resolve("palettes/basic.pal"));
        Files.copy(Path.of("src/test/resources/tilesets/ground.tileset"), project.resolve("ground.tileset"));
    }

    private int run(String... args) throws Exception {
        outBuf.reset();
        errBuf.reset();
        return new MockupCommand(project).run(List.of(args), new PrintStream(outBuf, true, StandardCharsets.UTF_8),
                new PrintStream(errBuf, true, StandardCharsets.UTF_8));
    }

    private String out() { return outBuf.toString(StandardCharsets.UTF_8); }

    @Test
    void parsesLegendAndMap() throws Exception {
        MockupParser.Mockup m = MockupParser.parse("# test\ng = grass\ne = edge\n\nmap\n  eee\n  g.g\n", "t", ground());
        assertEquals(3, m.width());
        assertEquals(2, m.height());
        assertEquals("edge", m.tile(1, 0));
        assertEquals("grass", m.tile(2, 1));
        assertNull(m.tile(1, 1), "'.' is an empty cell");
    }

    @Test
    void severalTilesAreScatteredTheSameWayEveryTime() throws Exception {
        String text = "g = grass grass_b\nmap\n" + ("g".repeat(8) + "\n").repeat(8);
        MockupParser.Mockup a = MockupParser.parse(text, "t", ground()), b = MockupParser.parse(text, "t", ground());
        Set<String> used = new HashSet<>();
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                assertEquals(a.tile(x, y), b.tile(x, y));
                used.add(a.tile(x, y));
            }
        }
        assertEquals(Set.of("grass", "grass_b"), used);
    }

    @Test
    void errors() throws Exception {
        List<String> e = assertThrows(ParseException.class, () -> MockupParser.parse(
                "g = grass\ng = edge\nx = nope\nhello\n. = grass\nmap\n  gg\n  g\n  gq\n  g g\n", "t", ground())).errors();
        assertEquals("t:2: 'g' is already defined on line 1", e.get(0));
        assertTrue(e.get(1).startsWith("t:3: no tile 'nope' in tileset 'ground' (tiles: grass, grass_b, edge, seam)"));
        assertEquals("t:4: expected '<character> = <tile> [<tile> ...]' or 'map'", e.get(2));
        assertEquals("t:5: '.' can't be a legend character ('.' is an empty cell)", e.get(3));
        assertEquals("t:8: map row 1 is 1 wide; the first row is 2", e.get(4));
        assertEquals("t:9: map cell (1,2): 'q' isn't in the legend", e.get(5));
        assertEquals("t:10: map rows can't contain spaces; use one character per tile", e.get(6));
        assertEquals(List.of("t: missing 'map' followed by the map rows"),
                assertThrows(ParseException.class, () -> MockupParser.parse("g = grass\n", "t", ground())).errors());
    }

    @Test
    void tilesAreDrawnSideBySideWithNoGap() throws Exception {
        Tileset t = ground();
        MockupSheet.Result r = MockupSheet.render(t, MockupParser.parse("s = seam\nmap\n  ss\n", "t", t), "x", 2, false);
        Rectangle map = r.map();
        assertEquals(16, map.width, "2 tiles of 4 px at 2x");
        // seam is wwkK in every row: the first copy ends in K (black), the second starts with w (white).
        assertEquals(0xFF000000, r.image().getRGB(map.x + 7, map.y));
        assertEquals(0xFFFFFFFF, r.image().getRGB(map.x + 8, map.y));
    }

    @Test
    void commandWritesTheImageAndHandlesVariantsAndErrors() throws Exception {
        Files.writeString(project.resolve("patch.mockup"), "g = grass grass_b\ne = edge\nmap\n  ggg\n  eee\n");
        assertEquals(0, run("ground.tileset", "patch.mockup", "--grid"), out() + errBuf);
        assertTrue(out().contains("wrote preview/ground/mockup-patch.png (3x2 tiles, scale 4x)"), out());
        BufferedImage img = ImageIO.read(project.resolve("preview/ground/mockup-patch.png").toFile());
        assertTrue(img.getWidth() >= 3 * 4 * 4);

        assertEquals(1, run("ground.tileset", "patch.mockup", "--variant", "winter"));
        assertTrue(errBuf.toString(StandardCharsets.UTF_8).contains("no variant 'winter' in ground.tileset (it has none)"));
        assertEquals(2, run("ground.tileset"));
        assertEquals(1, run("ground.tileset", "missing.mockup"));
        Files.writeString(project.resolve("bad.mockup"), "g = lawn\nmap\n  g\n");
        assertEquals(1, run("ground.tileset", "bad.mockup"));
        assertTrue(out().contains("no tile 'lawn'"), out());
    }
}
