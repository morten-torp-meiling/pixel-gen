package pixelgen.parse;

import org.junit.jupiter.api.Test;
import pixelgen.model.Grid;
import pixelgen.model.Tileset;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TilesetParserTest extends ParserTest {

    private static final PaletteResolver BASIC = only("basic");

    @Override
    Tileset parse(String text) throws ParseException {
        return TilesetParser.parse(text, "t.tileset", "t", BASIC);
    }

    @Test
    void sampleFile() throws Exception {
        Tileset t = TilesetParser.parseFile(Path.of("src/test/resources/tilesets/ground.tileset"), "ground.tileset", BASIC);
        assertEquals("ground", t.name());
        assertEquals(List.of("grass", "grass_b", "edge", "seam"), t.tiles());
        assertEquals(3, t.columns());
        assertEquals(Tileset.Wrap.BOTH, t.wrap("grass"));
        assertEquals(Tileset.Wrap.X, t.wrap("edge"));
        assertEquals(Grid.of("ggGg", "gGgg", "gggG", "Gggg"), t.sprite().frame("grass_b").grid(), "derived by flipx");
    }

    @Test
    void sizeDefaultsTo16AndColumnsToAtMost8() throws Exception {
        String row = "g".repeat(16) + "\n";
        StringBuilder text = new StringBuilder("palette basic\n");
        for (int i = 0; i < 10; i++) text.append("tile t").append(i).append('\n').append(row.repeat(16)).append("end\n");
        Tileset t = parse(text.toString());
        assertEquals(16, t.tileWidth());
        assertEquals(16, t.tileHeight());
        assertEquals(8, t.columns());
        assertEquals(1, parse("palette basic\nsize 1 1\ntile a\ng\nend\n").columns());
    }

    @Test
    void variantsAndWrapOnDerivedTiles() throws Exception {
        Tileset t = parse("palette basic\nsize 1 1\ntile a wrap none\ng\nend\ntile b from a wrap y flipx\n"
                + "variant snow recolor g>w\n");
        assertEquals(Tileset.Wrap.NONE, t.wrap("a"));
        assertEquals(Tileset.Wrap.Y, t.wrap("b"));
        List<Tileset> all = t.withVariants();
        assertEquals("snow", all.get(1).name());
        assertEquals(Grid.of("w"), all.get(1).sprite().frame("b").grid());
        assertEquals(Tileset.Wrap.Y, all.get(1).wrap("b"));
    }

    @Test
    void sprite_onlyWordsAreRejected() {
        List<String> e = errorsOf("palette basic\nsize 1 1\nsprite x\nframe a\ng\nend\nanim go fps 2 loop: a\nanchor 0 0\n"
                + "tile b\ng\nend\n");
        assertEquals(List.of(
                "t.tileset:3: a tileset uses 'tileset' where a sprite uses 'sprite'",
                "t.tileset:4: a tileset uses 'tile' where a sprite uses 'frame'",
                "t.tileset:7: tiles can't be animated (yet); draw each look as its own tile",
                "t.tileset:8: tiles have no anchor; they sit on the tile grid"), e);
    }

    @Test
    void tilesetErrorsKeepTheirLineNumbersAndSpeakOfTiles() {
        List<String> e = errorsOf("palette basic\nsize 2 1\ncolumns 0\ncolumns 2\ntile a wrap sideways\ngg\nend\n"
                + "tile b\ng\nend\ntile c from nope\n");
        assertEquals(List.of(
                "t.tileset:3: expected 'columns <n>' with n from 1 to 256",
                "t.tileset:4: 'columns' is already set on line 3",
                "t.tileset:5: expected 'wrap both', 'wrap x', 'wrap y' or 'wrap none', not 'wrap sideways'",
                "t.tileset:9: tile 'b' row 0 is 1 wide; size is 2x1"), e.subList(0, 4));
        assertTrue(e.get(4).startsWith("t.tileset:11: tile 'c': no tile 'nope' to derive from (tiles: a, b, c)"), e.get(4));
    }

    @Test
    void noTiles() {
        assertEquals(List.of("t.tileset: the tileset has no tiles; add 'tile <name>' ... 'end'"),
                errorsOf("palette basic\n"));
    }

    @Test
    void translateLeavesQuotedNamesAlone() {
        assertEquals("tile 'frame_a' and 'sprite-x' in tileset 'tile <name>' (tiles: a, b)",
                TilesetParser.translate("frame 'frame_a' and 'sprite-x' in sprite 'frame <name>' (frames: a, b)", "t"));
        assertEquals("a variant can't have the tileset's own name 'ground'",
                TilesetParser.translate("a variant can't have the sprite's own name 'ground'", "t"));
    }

    @Test
    void translateLeavesTheFileAndPathsAlone() {
        String source = "assets/sprites/frames.tileset";
        assertEquals(source + ":3: tile 'a' has 1 rows",
                TilesetParser.translate(source + ":3: frame 'a' has 1 rows", source));
        assertEquals(source + ": the tileset has no tiles",
                TilesetParser.translate(source + ": the sprite has no frames", source));
        assertEquals(source + ":1: palette 'p' not found (C:\\sprites\\p.pal)",
                TilesetParser.translate(source + ":1: palette 'p' not found (C:\\sprites\\p.pal)", source));
        // Lines after the first are another file's errors.
        assertEquals(source + ":2: style 's' has errors:\n  styles/s.style:4: sprite frame",
                TilesetParser.translate(source + ":2: style 's' has errors:\n  styles/s.style:4: sprite frame", source));
    }

    @Test
    void aTilesetInAFolderNamedSpritesKeepsItsPath() {
        ParseException e = assertThrows(ParseException.class, () -> TilesetParser.parse(
                "palette basic\nsize 2 2\ntile a\n  gg\n  gg\nend\ntile b\n  g\nend\n", "sprites/frames.tileset", "t",
                n -> Palettes.load("basic", Path.of("src/test/resources"))));
        assertEquals(List.of("sprites/frames.tileset:7: tile 'b' has 1 rows; size is 2x2",
                "sprites/frames.tileset:8: tile 'b' row 0 is 1 wide; size is 2x2"), e.errors());
    }
}
