package pixelgen.parse;

import org.junit.jupiter.api.Test;
import pixelgen.model.Palette;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PaletteParserTest {

    private static Palette parse(String text) throws ParseException {
        return PaletteParser.parse(text, "t.pal", "t");
    }

    private static List<String> errorsOf(String text) {
        return assertThrows(ParseException.class, () -> parse(text)).errors();
    }

    private static void assertOneErrorContaining(String text, String part) {
        List<String> errors = errorsOf(text);
        assertEquals(1, errors.size(), errors.toString());
        assertTrue(errors.getFirst().contains(part), errors.getFirst());
    }

    @Test
    void translucentColoursKeepTheirAlpha() throws Exception {
        Palette p = parse("ramp fx unordered\n ~ #1c152055 shadow\n K #1c1520\n");
        Palette.Entry shadow = p.get('~');
        assertEquals(0x1C1520, shadow.rgb());
        assertEquals(0x55, shadow.alpha());
        assertEquals(0x551C1520, shadow.argb());
        assertTrue(shadow.translucent());
        assertEquals("#1c152055", shadow.hex());
        assertFalse(p.get('K').translucent());
        assertEquals(List.of(), p.warnings(), "same rgb but different alpha is not a duplicate");
        assertTrue(p.hasTranslucent());
        assertTrue(p.isOpaque('K'));
        assertFalse(p.isOpaque('~'));
    }

    @Test
    void invisibleColoursAreRejectedAndTranslucentOnesBelongInUnorderedRamps() throws Exception {
        assertOneErrorContaining("ramp a\n ~ #00000000\n K #000000\n", "t.pal:2: '~' has alpha 00, which is invisible");
        List<String> w = parse("ramp a\n K #000000\n ~ #ffffff80\n").warnings();
        assertTrue(w.stream().anyMatch(m -> m.contains("t.pal:3: '~' is translucent; put it in an unordered ramp")),
                w.toString());
    }

    @Test
    void parsesTheSampleFile() throws Exception {
        Palette p = PaletteParser.parseFile(Path.of("src/test/resources/palettes/basic.pal"));
        assertEquals("basic", p.name());
        assertEquals(7, p.size());
        assertEquals(List.of("grey", "green", "accents"), p.ramps().stream().map(Palette.Ramp::name).toList());
        assertEquals(0x4a9a3a, p.get('g').rgb());
        assertEquals("green dark", p.get('G').name());
        assertEquals("", p.get('k').name());
        assertEquals("grey", p.get('k').ramp());
        assertFalse(p.ramps().get(2).ordered());
        assertEquals(List.of(), p.warnings());
    }

    @Test
    void keepsFileOrderInEntries() throws Exception {
        Palette p = parse("ramp a\n z #000000\n a #ffffff\n");
        assertEquals(List.of('z', 'a'), p.entries().stream().map(Palette.Entry::symbol).toList());
    }

    @Test
    void nameDefaultsToFileNameAndCanBeSet() throws Exception {
        assertEquals("t", parse("ramp a\n K #000000\n").name());
        assertEquals("custom", parse("palette custom\nramp a\n K #000000\n").name());
    }

    @Test
    void ignoresCommentsBlankLinesAndWindowsLineEndings() throws Exception {
        Palette p = parse("# hi\r\n\r\n  # indented comment\r\nramp a\r\n K #000000 ink\r\n");
        assertEquals("ink", p.get('K').name());
    }

    @Test
    void reportsBadHexWithLineNumber() {
        assertOneErrorContaining("ramp a\n K #12345\n", "t.pal:2: '#12345' is not a colour");
    }

    @Test
    void reportsDuplicateSymbolWithFirstLine() {
        assertOneErrorContaining("ramp a\n K #000000\n K #111111\n", "t.pal:3: symbol 'K' is already used on line 2");
    }

    @Test
    void rejectsTransparentAndHashAsSymbols() {
        assertOneErrorContaining("ramp a\n . #000000\n", "'.' means transparent");
        // A line starting with '#' is a comment, so '#' can never define a colour.
        assertOneErrorContaining("ramp a\n # #000000\n", "ramp 'a' has no colours");
    }

    @Test
    void rejectsMultiCharacterSymbols() {
        assertOneErrorContaining("ramp a\n Kk #000000\n", "unknown line 'Kk #000000'");
    }

    @Test
    void rejectsColoursOutsideARamp() {
        assertOneErrorContaining("K #000000\n", "outside a ramp");
    }

    @Test
    void rejectsMissingColourValue() {
        assertOneErrorContaining("ramp a\n K\n", "colour 'K' has no value");
    }

    @Test
    void rejectsEmptyAndDuplicateRamps() {
        assertOneErrorContaining("ramp a\nramp b\n K #000000\n", "ramp 'a' has no colours");
        assertOneErrorContaining("ramp a\n K #000000\nramp a\n k #111111\n", "ramp 'a' is already defined on line 1");
    }

    @Test
    void rejectsBadRampFlag() {
        assertTrue(errorsOf("ramp a sorted\n K #000000\n").getFirst().contains("'ramp <name> unordered'"));
    }

    @Test
    void rejectsEmptyPalette() {
        assertOneErrorContaining("# nothing\n", "the palette has no colours");
    }

    @Test
    void reportsAllErrorsAtOnce() {
        List<String> errors = errorsOf("ramp a\n K #zzzzzz\n k\n Kk #000000\n");
        assertEquals(3, errors.size(), errors.toString());
    }

    @Test
    void warnsWhenAnOrderedRampGetsDarker() throws Exception {
        Palette p = parse("ramp a\n w #ffffff\n K #000000\n");
        assertEquals(1, p.warnings().size());
        assertTrue(p.warnings().getFirst().contains("'K' is not lighter than 'w'"), p.warnings().getFirst());
    }

    @Test
    void warnsAboutStepsThatAreHardToTellApart() throws Exception {
        Palette p = parse("ramp a\n a #606060\n b #636363\n");
        assertTrue(p.warnings().getFirst().contains("very close in lightness (step 0.0"), p.warnings().toString());
    }

    @Test
    void unorderedRampsSkipTheLightnessCheck() throws Exception {
        assertEquals(List.of(), parse("ramp a unordered\n w #ffffff\n K #000000\n").warnings());
    }

    @Test
    void warnsAboutTheSameColourUnderTwoSymbols() throws Exception {
        Palette p = parse("ramp a unordered\n a #102030\n b #102030\n");
        assertTrue(p.warnings().getFirst().contains("'b' has the same colour #102030 as 'a'"), p.warnings().toString());
    }
}
