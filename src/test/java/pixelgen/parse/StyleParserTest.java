package pixelgen.parse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pixelgen.model.Effects;
import pixelgen.model.Light;
import pixelgen.model.Style;

import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StyleParserTest extends ParserTest {

    /** Every palette name resolves to the 'basic' test palette (symbols K k w G g y r). */
    private static final PaletteResolver BASIC = name -> {
        if (name.equals("missing")) throw new NoSuchFileException("palettes/missing.pal");
        return Palettes.load("basic", RESOURCES);
    };

    @TempDir Path dir;

    @Override
    Style parse(String text) throws ParseException {
        return StyleParser.parse(text, "t.style", "t", dir, BASIC);
    }

    @Test
    void readsTheSampleStyle() throws Exception {
        Style s = StyleParser.parseFile(RESOURCES.resolve("styles/basic.style"), "styles/basic.style",
                PaletteResolver.cached(RESOURCES));
        assertEquals("basic", s.name());
        assertEquals("basic", s.palette().name());
        assertEquals(Light.TOP_RIGHT, s.light());
        assertEquals('K', s.outline());
        assertEquals(Effects.OutlineMode.SELECTIVE, s.outlineMode());
        assertEquals('k', s.shadow());
        assertEquals(8, s.tileWidth());
        assertEquals(8, s.tileHeight());
        assertEquals(RESOURCES.resolve("styles/basic.md").toAbsolutePath().normalize(), s.guide());
        assertEquals(List.of(), s.warnings());
    }

    @Test
    void onlyThePaletteIsRequired() throws Exception {
        Files.writeString(dir.resolve("t.md"), "# t\n");
        Style s = parse("# just a palette\npalette basic\n");
        assertEquals("t", s.name());
        assertEquals(Light.TOP_LEFT, s.light());
        assertFalse(s.hasOutline());
        assertFalse(s.hasShadow());
        assertEquals(16, s.tileWidth());
        assertEquals(16, s.tileHeight());
        assertEquals(dir.resolve("t.md"), s.guide());
        assertEquals(List.of(), s.warnings());
    }

    @Test
    void outlineModeDefaultsToOuter() throws Exception {
        Style s = parse("palette basic\noutline k\n");
        assertEquals('k', s.outline());
        assertEquals(Effects.OutlineMode.OUTER, s.outlineMode());
    }

    @Test
    void styleLineNamesTheStyleAndItsDefaultGuide() throws Exception {
        Files.writeString(dir.resolve("mine.md"), "# mine\n");
        Style s = parse("style mine\npalette basic\n");
        assertEquals("mine", s.name());
        assertEquals(dir.resolve("mine.md"), s.guide());
        assertEquals(List.of(), s.warnings());
    }

    @Test
    void missingGuideIsAWarning() throws Exception {
        assertEquals(List.of("t.style: no guide t.md next to the style; write the style's rules there, "
                + "or name another file with 'guide <file.md>'"), parse("palette basic\n").warnings());
        List<String> w = parse("palette basic\nguide rules.md\n").warnings();
        assertEquals(1, w.size());
        assertTrue(w.getFirst().startsWith("t.style:2: guide 'rules.md' not found ("), w.getFirst());
    }

    @Test
    void reportsEveryErrorSortedByLine() {
        List<String> e = errorsOf("""
                style bad name
                light up
                outline K sideways
                shadow ~
                tile 0 16
                guide
                colour blue
                light top-left
                palette basic
                """);
        assertEquals(List.of(
                "t.style:1: expected 'style <name>' (letters, digits, _ and -)",
                "t.style:2: expected 'light <direction>', with top-left, top-right, bottom-left or bottom-right",
                "t.style:3: unknown outline mode 'sideways'; expected outer or selective",
                "t.style:4: '~' is not in palette 'basic'",
                "t.style:5: tile size must be between 1 and 256",
                "t.style:6: expected 'guide <file.md>', a file next to the style",
                "t.style:7: unknown line 'colour blue'; a style has these lines: "
                        + "style, palette, light, outline, shadow, tile, guide",
                "t.style:8: 'light' is already set on line 2"), e);
    }

    @Test
    void shadowWithASizeExplainsThatSpritesGiveTheSize() {
        assertEquals(List.of("t.style:2: a style sets only the shadow's colour ('shadow k'); "
                + "each sprite gives its size with 'shadow <width> <height>'"),
                errorsOf("palette basic\nshadow k 12 3\n"));
    }

    @Test
    void paletteIsRequiredAndMustLoad() {
        assertEquals(List.of("t.style: missing 'palette <name>'"), errorsOf("light top-left\n"));
        List<String> e = errorsOf("palette missing\noutline K\n");
        assertEquals(1, e.size());
        assertTrue(e.getFirst().startsWith("t.style:1: palette 'missing' not found"), e.getFirst());
    }

    @Test
    void outlineColourMustBeInThePalette() {
        assertEquals(List.of("t.style:2: 'Q' is not in palette 'basic'"), errorsOf("palette basic\noutline Q\n"));
    }

    @Test
    void tileSizeMustBeTwoWholeNumbers() {
        assertEquals(List.of("t.style:2: expected 'tile <width> <height>'"), errorsOf("palette basic\ntile 16\n"));
        assertEquals(List.of("t.style:2: 'big' is not a whole number; expected 'tile <width> <height>'"),
                errorsOf("palette basic\ntile big 16\n"));
    }

    @Test
    void outlineModeWords() {
        for (Effects.OutlineMode m : Effects.OutlineMode.values()) assertEquals(m, Effects.OutlineMode.parse(m.word()));
        assertEquals("selective", Effects.OutlineMode.SELECTIVE.word());
        assertNull(Effects.OutlineMode.parse("SELECTIVE"));
    }

    @Test
    void lightWords() {
        for (Light l : Light.values()) assertEquals(l, Light.parse(l.word()));
        assertNull(Light.parse("left"));
        assertEquals(1, Light.TOP_RIGHT.lx());
        assertEquals(-1, Light.TOP_RIGHT.ly());
        assertEquals(-1, Light.BOTTOM_LEFT.lx());
        assertEquals(1, Light.BOTTOM_LEFT.ly());
    }
}
