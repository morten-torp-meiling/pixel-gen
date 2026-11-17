package pixelgen.parse;

import org.junit.jupiter.api.Test;
import pixelgen.model.Grid;
import pixelgen.model.Sprite;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Frames built from parts with stamp and recolor, and sprite variants (basic palette: K k w G g y r). */
class SpriteCompositionTest extends ParserTest {
    private static final PaletteResolver BASIC = only("basic");
    private static final String HEAD = "size 4 3\npalette basic\npart dot\nK\nend\npart bar\nkkk\nend\npart corner\nG.\nGG\nend\n";

    @Override
    Sprite parse(String text) throws ParseException {
        return SpriteParser.parse(text, "t.sprite", "t", BASIC);
    }

    private Grid frame(String body) throws ParseException {
        return parse(HEAD + "frame a\n" + body + "end\n").frame("a").grid();
    }

    // ---- stamps ----

    @Test
    void stampsLandAtTheirTopLeftCorner() throws Exception {
        assertEquals(Grid.of("....", ".kkk", "...."), frame("stamp bar 1 1\n"));
        assertEquals(Grid.of("....", "....", "...K"), frame("stamp dot 3 2\n"));
    }

    @Test
    void stampsApplyInDrawOrderSoLaterOnesAreOnTop() throws Exception {
        assertEquals(Grid.of("kKk.", "....", "...."), frame("stamp bar 0 0\nstamp dot 1 0\n"));
        assertEquals(Grid.of("kkk.", "....", "...."), frame("stamp dot 1 0\nstamp bar 0 0\n"));
    }

    @Test
    void transparentPartCellsLeaveWhatIsUnderneath() throws Exception {
        // corner's top-right cell is '.', so the bar's pixel at (1,0) stays.
        assertEquals(Grid.of("Gkk.", "GG..", "...."), frame("stamp bar 0 0\nstamp corner 0 0\n"));
    }

    @Test
    void stampsCanBeFlipped() throws Exception {
        assertEquals(Grid.of(".G..", "GG..", "...."), frame("stamp corner 0 0 flipx\n"));
        assertEquals(Grid.of("GG..", "G...", "...."), frame("stamp corner 0 0 flipy\n"));
        assertEquals(Grid.of("GG..", ".G..", "...."), frame("stamp corner 0 0 flipx flipy\n"));
    }

    @Test
    void stampsGoOnTopOfGridRows() throws Exception {
        assertEquals(Grid.of("wwww", "wKww", "wwww"), frame("wwww\nwwww\nwwww\nstamp dot 1 1\n"));
    }

    @Test
    void negativeAndEdgeOffsetsAreCutOffWithAWarning() throws Exception {
        Sprite s = parse(HEAD + "frame a\nstamp bar 2 0\nend\n");
        assertEquals(Grid.of("..kk", "....", "...."), s.frame("a").grid());
        assertEquals(1, s.warnings().size());
        assertTrue(s.warnings().getFirst().contains("t.sprite:14: frame 'a': part 'bar' at (2,0) is partly outside"),
                s.warnings().getFirst());
        assertEquals(Grid.of("GG..", "....", "...."), frame("stamp corner 0 -1\n"));  // only its bottom row is inside
    }

    @Test
    void partsCanBeUsedByManyFrames() throws Exception {
        Sprite s = parse(HEAD + "frame a\nstamp dot 0 0\nend\nframe b\nstamp dot 3 0\nend\n");
        assertEquals('K', s.frame("a").grid().at(0, 0));
        assertEquals('K', s.frame("b").grid().at(3, 0));
    }

    // ---- recolor ----

    @Test
    void recolorChangesEverythingDrawnSoFar() throws Exception {
        assertEquals(Grid.of("ggg.", "....", "...K"), frame("stamp bar 0 0\nrecolor k>g\nstamp dot 3 2\n"));
    }

    @Test
    void recolorSwapsSimultaneously() throws Exception {
        // "Kkk." with K and k swapped at once; one after the other would give "kkk." or "KKK.".
        assertEquals(Grid.of("kKK.", "....", "...."), frame("stamp bar 0 0\nstamp dot 0 0\nrecolor K>k k>K\n"));
    }

    @Test
    void recolorToTransparentErases() throws Exception {
        assertEquals(Grid.of("K...", "....", "...."), frame("ww.w\n....\n....\nrecolor w>.\nstamp dot 0 0\n"));
    }

    // ---- variants ----

    @Test
    void variantsRecolourEveryFrameAndPart() throws Exception {
        Sprite s = parse(HEAD + "frame a\nstamp bar 0 0\nstamp dot 3 2\nend\nvariant t_green recolor k>g K>r\n");
        assertEquals(List.of("t_green"), List.copyOf(s.variants().keySet()));
        Sprite v = s.variant("t_green");
        assertEquals("t_green", v.name());
        assertEquals(Grid.of("ggg.", "....", "...r"), v.frame("a").grid());
        assertEquals(Grid.of("ggg"), v.parts().get("bar"));
        assertEquals(Grid.of("kkk.", "....", "...K"), s.frame("a").grid(), "the original is unchanged");
    }

    @Test
    void withVariantsListsTheSpriteThenEachVariant() throws Exception {
        Sprite s = parse(HEAD + "frame a\nstamp dot 0 0\nend\nvariant red recolor K>r\nvariant white recolor K>w\n");
        assertEquals(List.of("t", "red", "white"), s.withVariants().stream().map(Sprite::name).toList());
    }

    // ---- errors ----

    @Test
    void unknownPartListsTheDefinedParts() {
        assertOneError(HEAD + "frame a\nstamp head 0 0\nend\n", "t.sprite:14: frame 'a': unknown part 'head' (parts: dot, bar, corner)");
        assertOneError("size 1 1\npalette basic\nframe a\nstamp head 0 0\nend\n", "unknown part 'head' (no parts are defined)");
    }

    @Test
    void aBrokenPartIsReportedOnceNotAtEveryStamp() {
        List<String> errors = errorsOf("size 2 1\npalette basic\npart p\nQ\nend\nframe a\nstamp p 0 0\nend\nframe b\nstamp p 1 0\nend\n");
        assertEquals(1, errors.size(), errors.toString());
        assertTrue(errors.getFirst().contains("part 'p' pixel (0,0): 'Q'"), errors.getFirst());
    }

    @Test
    void badStampSyntax() {
        assertOneError(HEAD + "frame a\nstamp dot 1\nend\n", "expected 'stamp <part> <x> <y> [flipx] [flipy]'");
        assertOneError(HEAD + "frame a\nstamp dot one 1\nend\n", "'one' is not a whole number");
        assertOneError(HEAD + "frame a\nstamp dot 1 1 mirror\nend\n", "unknown stamp option 'mirror'");
    }

    @Test
    void badRecolorSyntaxOrSymbols() {
        assertOneError(HEAD + "frame a\nstamp dot 0 0\nrecolor K-r\nend\n", "'K-r' is not a colour swap");
        assertOneError(HEAD + "frame a\nstamp dot 0 0\nrecolor K>r K>w\nend\n", "'K' is recoloured twice");
        assertOneError(HEAD + "frame a\nstamp dot 0 0\nrecolor .>r\nend\n", "transparent pixels can't be recoloured");
        assertOneError(HEAD + "frame a\nstamp dot 0 0\nrecolor K>Q\nend\n", "t.sprite:15: 'Q' is not in palette 'basic'");
    }

    @Test
    void rowsMustComeBeforeOperations() {
        assertOneError("size 1 1\npalette basic\npart p\nK\nend\nframe a\nstamp p 0 0\nK\nend\n",
                "t.sprite:8: frame 'a': grid rows must come before 'stamp', 'recolor', 'outline' and 'shadow' lines");
    }

    @Test
    void partsCannotStamp() {
        assertOneError(HEAD + "part q\nstamp dot 0 0\nend\nframe a\nstamp dot 0 0\nend\n", "part 'q': parts can't contain 'stamp' lines");
    }

    @Test
    void operationsOutsideAFrameGetAHint() {
        assertOneError(HEAD + "stamp dot 0 0\nframe a\nstamp dot 0 0\nend\n", "'stamp' lines belong inside a frame");
    }

    @Test
    void badVariants() {
        String frame = "frame a\nstamp dot 0 0\nend\n";
        assertOneError(HEAD + frame + "variant blue\n", "expected 'variant <name> recolor <from>><to> ...'");
        assertOneError(HEAD + frame + "variant blue swap K>r\n", "expected 'variant <name> recolor");
        assertOneError(HEAD + frame + "variant blue recolor K>Q\n", "'Q' is not in palette 'basic'");
        assertOneError(HEAD + frame + "variant v recolor K>r\nvariant v recolor K>w\n", "variant 'v' is already defined on line 16");
        assertOneError(HEAD + frame + "variant t recolor K>r\n", "a variant can't have the sprite's own name 't'");
    }
}
