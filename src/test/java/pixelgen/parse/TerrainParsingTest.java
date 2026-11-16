package pixelgen.parse;

import org.junit.jupiter.api.Test;
import pixelgen.model.Grid;
import pixelgen.model.Tileset;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** {@code terrain} blocks and {@code blend} lines, in tilesets (and so in sprites). */
class TerrainParsingTest {

    private static final PaletteResolver BASIC = n -> Palettes.load("basic", Path.of("src/test/resources"));

    private static final String HEAD = "palette basic\nsize 4 4\n"
            + "part raised\ngggg\ngggg\ngggg\ngggg\nend\npart lower\nwwww\nwwww\nwwww\nwwww\nend\n";

    private static final String TERRAIN = "terrain gw\n  raised raised\n  lower lower\n  bank G\n  lip y\n  shadow k\n"
            + "  n 2 2 2 2\n  w 2 2 2 2\n  n2 2 1 3 2\nend\n";

    private static Tileset parse(String text) throws ParseException {
        return TilesetParser.parse(text, "t.tileset", "t", BASIC);
    }

    private static List<String> errors(String text) {
        return assertThrows(ParseException.class, () -> parse(text)).errors();
    }

    @Test
    void blendMakesTheTileAndLaterLinesTouchItUp() throws Exception {
        Tileset t = parse(HEAD + TERRAIN
                + "tile edge wrap x\n  blend gw n\nend\n"
                + "tile corner wrap none\n  blend gw n|w\nend\n"
                + "tile inner wrap none\n  blend gw n&w\n  recolor w>K\nend\n");
        assertEquals(Grid.of("gggg", "GGGG", "kkkk", "wwww"), t.sprite().frame("edge").grid());
        assertEquals(Grid.of("gggg", "ggGG", "gGkk", "gGkw"), t.sprite().frame("corner").grid());
        assertEquals(Grid.of("gGkK", "GGkK", "kkKK", "KKKK"), t.sprite().frame("inner").grid());
        assertEquals(List.of(), t.sprite().warnings());
    }

    @Test
    void terrainErrors() {
        List<String> e = errors(HEAD
                + "terrain bad\n  raised nope\n  lower lower\n  bank Q\n  bank G\n  n 2 2 2\n  w 2 2 9 2\n  x 1 1 1 1\n"
                + "  middle\nend\n"
                + "tile a\n  blend bad n\nend\n");
        assertEquals(List.of(
                "t.tileset:16: unknown part 'nope' (parts: raised, lower)",
                "t.tileset:18: 'Q' is not in palette 'basic'",
                "t.tileset:19: 'bank' is already set on line 18",
                "t.tileset:20: profile 'n': has 3 values; it needs one per column (4)",
                "t.tileset:21: profile 'w': '9' isn't a depth from 0 to 4"), e.subList(0, 5));
        assertTrue(e.get(5).startsWith("t.tileset:22: unknown line in terrain 'bad'"), e.get(5));
        assertTrue(e.get(6).startsWith("t.tileset:23: unknown line in terrain 'bad'"), e.get(6));
        assertEquals(7, e.size(), "the tile using the broken terrain adds no follow-on error: " + e);
    }

    @Test
    void blendErrors() {
        List<String> e = errors(HEAD + TERRAIN
                + "tile a\n  blend gw n|w&e\nend\n"
                + "tile b\n  blend gw q\nend\n"
                + "tile c\n  blend nope n\nend\n"
                + "tile d\n  blend gw s\nend\n"
                + "tile e\n  stamp raised 0 0\n  blend gw n\nend\n"
                + "tile f\n  blend gw\nend\n");
        assertEquals(List.of(
                "t.tileset:26: 'n|w&e' mixes | and &; use | for an outer corner or & for an inner one",
                "t.tileset:29: 'q' isn't a profile name (they start with n, s, w or e)",
                "t.tileset:32: tile 'c': unknown terrain 'nope' (terrains: gw)",
                "t.tileset:35: tile 'd': terrain 'gw' has no profile s (profiles: n, n2, w)",
                "t.tileset:39: tile 'e': 'blend' makes the whole tile, so it comes first, in a tile without grid rows;"
                        + " touch it up with lines after it",
                "t.tileset:42: expected 'blend <terrain> <profile>[|<profile>...] or <profile>&<profile>...',"
                        + " like 'blend grass_dirt n|w'"), e);
    }

    @Test
    void profilesThatWontMeetAreWarnedAbout() throws Exception {
        Tileset t = parse(HEAD + "terrain gw\n  raised raised\n  lower lower\n  n 2 2 2 2\n  n2 3 2 2 3\n"
                + "  w 2 2 2 1\nend\ntile a\n  blend gw n\nend\n");
        assertEquals(List.of(
                "t.tileset:19: profile 'n2': ends at different depths from 'n', so their tiles won't meet",
                "t.tileset:20: profile 'w': starts at 2 and ends at 1; a tile using it won't fit next to a copy of itself"),
                t.sprite().warnings());
    }
}
