package pixelgen.parse;

import org.junit.jupiter.api.Test;
import pixelgen.model.Grid;
import pixelgen.model.Palette;
import pixelgen.model.Sprite;

import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SpriteParserTest {

    private static final Path RESOURCES = Path.of("src/test/resources");

    /** Every test palette name resolves to the 'basic' test palette (symbols K k w G g y r). */
    private static final PaletteResolver BASIC = name -> {
        if (name.equals("missing")) throw new NoSuchFileException("palettes/missing.pal");
        if (name.equals("broken")) throw new ParseException(List.of("broken.pal:3: bad hex"));
        return Palettes.load("basic", RESOURCES);
    };

    /** A valid 4x2 sprite header; tests append blocks to it. */
    private static final String HEAD = "size 4 2\npalette basic\n";

    private static Sprite parse(String text) throws ParseException {
        return SpriteParser.parse(text, "t.sprite", "t", BASIC);
    }

    private static List<String> errorsOf(String text) {
        return assertThrows(ParseException.class, () -> parse(text)).errors();
    }

    private static void assertOneError(String text, String expectedPart) {
        List<String> errors = errorsOf(text);
        assertEquals(1, errors.size(), "errors: " + errors);
        assertTrue(errors.getFirst().contains(expectedPart), errors.getFirst());
    }

    // ---- valid input ----

    @Test
    void parsesTheSampleApple() throws Exception {
        Sprite s = SpriteParser.parseFile(RESOURCES.resolve("sprites/apple.sprite"), "apple.sprite",
                PaletteResolver.cached(RESOURCES));
        assertEquals("apple", s.name());
        assertEquals(16, s.width());
        assertEquals(16, s.height());
        assertEquals("basic", s.palette().name());
        assertEquals(8, s.anchorX());
        assertEquals(14, s.anchorY());
        assertEquals(Grid.of(".gg", "gG."), s.parts().get("leaf"));
        Grid idle = s.frame("idle").grid();
        assertEquals(16, idle.height(), "the comment line inside the grid is not a row");
        assertEquals('G', idle.at(7, 2));
        assertEquals('w', idle.at(5, 6));
        assertEquals(List.of(), s.warnings());
    }

    @Test
    void nameDefaultsToFileNameAndAnchorToBottomCentre() throws Exception {
        Sprite s = parse(HEAD + "frame a\nKKKK\nkkkk\nend\n");
        assertEquals("t", s.name());
        assertEquals(2, s.anchorX());
        assertEquals(1, s.anchorY());
    }

    @Test
    void directivesMayComeInAnyOrder() throws Exception {
        Sprite s = parse("frame a\nKKKK\nkkkk\nend\npalette basic\nsize 4 2\nsprite late\n");
        assertEquals("late", s.name());
        assertEquals(4, s.frame("a").grid().width());
    }

    @Test
    void keepsFramesAndPartsInFileOrder() throws Exception {
        Sprite s = parse(HEAD + "frame b\nKKKK\nKKKK\nend\nframe a\nkkkk\nkkkk\nend\npart z\nK\nend\npart y\nk\nend\n");
        assertEquals(List.of("b", "a"), List.copyOf(s.frames().keySet()));
        assertEquals(List.of("z", "y"), List.copyOf(s.parts().keySet()));
    }

    @Test
    void aPartAndAFrameMayShareAName() throws Exception {
        Sprite s = parse(HEAD + "part body\nKK\nend\nframe body\nKKKK\nKKKK\nend\n");
        assertNotNull(s.parts().get("body"));
        assertNotNull(s.frame("body"));
    }

    @Test
    void acceptsWindowsLineEndingsAndIndentation() throws Exception {
        Sprite s = parse("size 2 1\r\n  palette basic\r\nframe a\r\n    Kk\r\n  end\r\n");
        assertEquals(Grid.of("Kk"), s.frame("a").grid());
    }

    @Test
    void warnsAboutFramesWithNoVisiblePixels() throws Exception {
        Sprite s = parse(HEAD + "frame a\n....\n....\nend\n");
        assertTrue(s.warnings().getFirst().contains("frame 'a' has no visible pixels"), s.warnings().toString());
    }

    // ---- one test per kind of error ----

    @Test
    void missingSizeOrPalette() {
        assertOneError("palette basic\nframe a\nK\nend\n", "missing 'size <width> <height>'");
        assertOneError("size 1 1\nframe a\nK\nend\n", "missing 'palette <name>'");
    }

    @Test
    void badSize() {
        String frame = "frame a\nK\nend\n";  // no size check is possible, so no follow-on errors
        assertOneError("size 0 4\npalette basic\n" + frame, "size must be between 1 and 256");
        assertOneError("size 300 4\npalette basic\n" + frame, "size must be between 1 and 256");
        assertOneError("size 4\npalette basic\n" + frame, "expected 'size <width> <height>'");
        assertOneError("size four 4\npalette basic\n" + frame, "'four' is not a whole number");
    }

    @Test
    void paletteNotFoundOrBroken() {
        assertOneError("size 1 1\npalette missing\nframe a\n.\nend\n", "t.sprite:2: palette 'missing' not found");
        List<String> errors = errorsOf("size 1 1\npalette broken\nframe a\n.\nend\n");
        assertEquals(List.of("t.sprite:2: palette 'broken' has errors:\n  broken.pal:3: bad hex"), errors);
    }

    @Test
    void frameRowWithWrongWidth() {
        assertOneError(HEAD + "frame a\nKKKK\nKKK\nend\n", "t.sprite:5: frame 'a' row 1 is 3 wide; size is 4x2");
    }

    @Test
    void frameWithWrongNumberOfRows() {
        assertOneError(HEAD + "frame a\nKKKK\nKKKK\nKKKK\nend\n", "t.sprite:3: frame 'a' has 3 rows; size is 4x2");
    }

    @Test
    void partWithRaggedRows() {
        assertOneError(HEAD + "part p\nKK\nK\nend\nframe a\nKKKK\nKKKK\nend\n",
                "t.sprite:5: part 'p' row 1 is 1 wide; the first row is 2");
    }

    @Test
    void unknownSymbolReportsPixelCoordinates() {
        assertOneError(HEAD + "frame a\nKKKK\nKKQK\nend\n", "t.sprite:5: frame 'a' pixel (2,1): 'Q' is not in palette 'basic'");
    }

    @Test
    void manyUnknownSymbolsAreSummarised() {
        List<String> errors = errorsOf("size 16 1\npalette basic\nframe a\nQQQQQQQQQQQQQQQQ\nend\n");
        assertEquals(SpriteParser.MAX_SYMBOL_ERRORS_PER_GRID + 1, errors.size(), errors.toString());
        assertTrue(errors.getLast().contains("6 more unknown symbols not listed"), errors.getLast());
    }

    @Test
    void duplicateFrameOrPart() {
        assertOneError(HEAD + "frame a\nKKKK\nKKKK\nend\nframe a\nkkkk\nkkkk\nend\n",
                "t.sprite:7: frame 'a' is already defined on line 3");
        assertOneError(HEAD + "part p\nK\nend\npart p\nk\nend\nframe a\nKKKK\nKKKK\nend\n",
                "t.sprite:6: part 'p' is already defined on line 3");
    }

    @Test
    void duplicateDirective() {
        assertOneError(HEAD + "size 4 2\nframe a\nKKKK\nKKKK\nend\n", "t.sprite:3: 'size' is already set on line 1");
    }

    @Test
    void blockWithoutEndAtEndOfFile() {
        assertOneError(HEAD + "frame a\nKKKK\nKKKK\n", "t.sprite:3: frame 'a' has no 'end'");
    }

    @Test
    void blockWithoutEndBeforeTheNextOneIsReportedOnce() throws Exception {
        List<String> errors = errorsOf(HEAD + "frame a\nKKKK\nKKKK\nframe b\nkkkk\nkkkk\nend\n");
        assertEquals(List.of("t.sprite:3: frame 'a' has no 'end' before line 6"), errors);
    }

    @Test
    void endWithoutBlock() {
        assertOneError(HEAD + "end\nframe a\nKKKK\nKKKK\nend\n", "t.sprite:3: 'end' without a matching");
    }

    @Test
    void spacesInsideAGridRow() {
        assertOneError(HEAD + "frame a\nKK KK\nKKKK\nend\n", "t.sprite:4: frame 'a': grid rows can't contain spaces");
    }

    @Test
    void gridRowOutsideABlockGetsAHint() {
        List<String> errors = errorsOf(HEAD + "KKKK\nframe a\nKKKK\nKKKK\nend\n");
        assertTrue(errors.getFirst().contains("grid rows belong between 'frame <name>' and 'end'"), errors.toString());
    }

    @Test
    void unknownDirective() {
        assertOneError(HEAD + "colour red\nframe a\nKKKK\nKKKK\nend\n", "t.sprite:3: unknown line 'colour red'");
    }

    @Test
    void noFrames() {
        assertOneError(HEAD + "part p\nK\nend\n", "the sprite has no frames");
    }

    @Test
    void emptyBlock() {
        assertOneError(HEAD + "frame a\nend\n", "t.sprite:3: frame 'a' is empty; add grid rows or 'stamp' lines");
        assertOneError(HEAD + "part p\nend\nframe a\nKKKK\nKKKK\nend\n", "t.sprite:3: part 'p' has no rows");
    }

    @Test
    void anchorOutsideTheFrame() {
        assertOneError(HEAD + "anchor 4 0\nframe a\nKKKK\nKKKK\nend\n", "t.sprite:3: anchor (4,0) is outside the 4x2 frame");
    }

    @Test
    void badNames() {
        assertOneError(HEAD + "sprite my apple\nframe a\nKKKK\nKKKK\nend\n", "expected 'sprite <name>'");
        assertTrue(errorsOf(HEAD + "frame a/b\nKKKK\nKKKK\nend\n").getFirst().contains("expected 'frame <name>'"));
    }

    @Test
    void reportsEveryErrorInOnePass() {
        List<String> errors = errorsOf("size 4 2\npalette basic\nframe a\nKKK\nKKQK\nend\nframe a\nKKKK\nKKKK\nend\nbogus\n");
        assertEquals(4, errors.size(), errors.toString());  // width, unknown symbol, duplicate frame, unknown line
    }

    @Test
    void errorsAreSortedByLineWithFileLevelErrorsFirst() {
        List<String> errors = errorsOf("palette basic\nanchor 9 9\nframe a\nQ\nend\nsize 1 1\nframe a\nK\nend\n");
        // Found in different passes, reported in file order.
        assertEquals(List.of(
                "t.sprite:2: anchor (9,9) is outside the 1x1 frame",
                "t.sprite:4: frame 'a' pixel (0,0): 'Q' is not in palette 'basic'",
                "t.sprite:7: frame 'a' is already defined on line 3"), errors);
    }

    @Test
    void paletteSymbolsComeFromTheResolvedPalette() throws Exception {
        Palette basic = Palettes.load("basic", RESOURCES);
        Sprite s = parse(HEAD + "frame a\nKkwG\ngyr.\nend\n");
        assertSame(basic.getClass(), s.palette().getClass());
        assertTrue(s.palette().contains('y'));
    }
}
