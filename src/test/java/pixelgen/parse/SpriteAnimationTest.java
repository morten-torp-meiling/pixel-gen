package pixelgen.parse;

import org.junit.jupiter.api.Test;
import pixelgen.model.Anim;
import pixelgen.model.Grid;
import pixelgen.model.Sprite;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Derived frames ('frame x from y ...') and 'anim' lines (fx palette: K k w | G g | y r | ~). */
class SpriteAnimationTest extends ParserTest {

    private static final PaletteResolver FX = only("fx");
    // 4x3, anchor (2,2). Frame 'r' (lines 4-8) has a mark in its top-left corner.
    private static final String HEAD = "size 4 3\npalette fx\nanchor 2 2\nframe r\nKg..\n.g..\n.gg.\nend\n";

    @Override
    Sprite parse(String text) throws ParseException {
        return SpriteParser.parse(text, "t.sprite", "t", FX);
    }

    private Grid frame(String extra, String name) throws ParseException {
        return parse(HEAD + extra).frame(name).grid();
    }

    // ---- derived frames ----

    @Test
    void flips() throws Exception {
        assertEquals(Grid.of("..gK", "..g.", ".gg."), frame("frame l from r flipx\n", "l"));
        assertEquals(Grid.of(".gg.", ".g..", "Kg.."), frame("frame u from r flipy\n", "u"));
    }

    @Test
    void shiftMovesTheImageAndWarnsWhenPixelsFallOff() throws Exception {
        Sprite s = parse(HEAD + "frame bob from r shift 1 0\n");
        assertEquals(Grid.of(".Kg.", "..g.", "..gg"), s.frame("bob").grid());
        assertEquals(List.of(), s.warnings());

        Sprite up = parse(HEAD + "frame up from r shift 0 -1\n");
        assertEquals(Grid.of(".g..", ".gg.", "...."), up.frame("up").grid());
        assertEquals(List.of("t.sprite:9: frame 'up': shift 0 -1 moves pixels out of the frame; they are cut off"),
                up.warnings());
    }

    @Test
    void recolorAndOptionsApplyInTheOrderWritten() throws Exception {
        assertEquals(Grid.of("..rk", "..r.", ".rr."), frame("frame x from r flipx recolor g>r K>k\n", "x"));
        // shift then flip differs from flip then shift
        assertEquals(Grid.of(".gK.", ".g..", "gg.."), frame("frame a from r shift 1 0 flipx\n", "a"));
        assertEquals(Grid.of("...g", "...g", "..gg"), frame("frame b from r flipx shift 1 0\n", "b"));
    }

    @Test
    void derivedFramesCanChainInAnyOrder() throws Exception {
        // 'b' is written before 'a', which it copies.
        Sprite s = parse(HEAD + "frame b from a recolor g>r\nframe a from r flipx\n");
        assertEquals(Grid.of("..rK", "..r.", ".rr."), s.frame("b").grid());
        assertEquals(List.of("r", "b", "a"), List.copyOf(s.frames().keySet()), "file order");
    }

    @Test
    void spriteEffectsAreDrawnAfterDerivingSoTheyAreNotFlipped() throws Exception {
        // The shadow sits on the anchor (2,2) in both frames, not mirrored to column 1.
        Sprite s = parse("size 4 3\npalette fx\nanchor 3 2\nshadow ~ 1 1\nframe r\n....\n....\n....\nend\n"
                + "frame l from r flipx\n");
        assertEquals(Grid.of("....", "....", "...~"), s.frame("r").grid());
        assertEquals(Grid.of("....", "....", "...~"), s.frame("l").grid());
    }

    @Test
    void variantsRecolourDerivedFrames() throws Exception {
        Sprite s = parse(HEAD + "frame l from r flipx\nvariant t_red recolor g>r\n");
        assertEquals(Grid.of("..rK", "..r.", ".rr."), s.variant("t_red").frame("l").grid());
    }

    @Test
    void derivedFrameErrors() {
        assertOneError(HEAD + "frame l from q flipx\n", "t.sprite:9: frame 'l': no frame 'q' to derive from (frames: r, l)");
        assertOneError(HEAD + "part p\nK\nend\nframe l from p\n", "no frame 'p' to derive from ('p' is a part; frames can use it with 'stamp')");
        assertOneError(HEAD + "frame l from r mirror\n", "unknown option 'mirror'; expected flipx, flipy, shift <dx> <dy> or recolor");
        assertOneError(HEAD + "frame l from r shift 1\n", "expected 'shift <dx> <dy>'");
        assertOneError(HEAD + "frame l from r shift 1 x\n", "'x' is not a whole number");
        assertOneError(HEAD + "frame l from r recolor\n", "expected at least one colour swap");
        assertOneError(HEAD + "frame l from r recolor g>Q\n", "t.sprite:9: 'Q' is not in palette 'fx'");
        assertOneError(HEAD + "frame l from\n", "expected 'frame <name> from <frame>");
        assertOneError(HEAD + "frame r from r flipx\n", "frame 'r' is already defined on line 4");
    }

    @Test
    void derivationLoopsAreReportedOnce() {
        assertOneError(HEAD + "frame a from b\nframe b from a\n", "frames can't derive from each other in a loop (a -> b -> a)");
        assertOneError(HEAD + "frame a from a flipx\n", "(a -> a)");
    }

    @Test
    void aBrokenSourceIsNotReportedAgainOnTheCopy() {
        List<String> errors = errorsOf("size 1 1\npalette fx\nframe r\nQ\nend\nframe l from r flipx\n");
        assertEquals(1, errors.size(), errors.toString());
        assertTrue(errors.getFirst().contains("'Q' is not in palette"), errors.getFirst());
    }

    // ---- anims ----

    @Test
    void animsKeepRateLoopingAndDurations() throws Exception {
        Sprite s = parse(HEAD + "frame l from r flipx\n"
                + "anim walk fps 8 loop: r l r*2 l*0.5\n"
                + "anim turn fps 12.5 once : l r\n"
                + "anim spin fps 4 once l\n");
        Anim walk = s.anim("walk");
        assertEquals(8.0, walk.fps());
        assertTrue(walk.loop());
        assertEquals(List.of(new Anim.Step("r", 1), new Anim.Step("l", 1), new Anim.Step("r", 2), new Anim.Step("l", 0.5)),
                walk.steps());
        assertEquals(4.5 / 8, walk.seconds(), 1e-9);
        assertFalse(s.anim("turn").loop());
        assertEquals(12.5, s.anim("turn").fps());
        assertEquals(List.of(new Anim.Step("l", 1)), s.anim("spin").steps());
        assertEquals(List.of("walk", "turn", "spin"), List.copyOf(s.anims().keySet()));
    }

    @Test
    void variantsKeepTheAnims() throws Exception {
        Sprite s = parse(HEAD + "anim idle fps 2 loop: r\nvariant t_red recolor g>r\n");
        assertEquals(s.anims(), s.variant("t_red").anims());
    }

    @Test
    void animsMayComeBeforeTheFramesTheyUse() throws Exception {
        Sprite s = parse("size 1 1\npalette fx\nanim blink fps 4 loop: on off\nframe on\nK\nend\nframe off\n.\nend\n");
        assertEquals(2, s.anim("blink").steps().size());
    }

    @Test
    void unknownFramesInAnAnimAreListedOnce() {
        List<String> errors = errorsOf(HEAD + "anim walk fps 8 loop: r x r x y\n");
        assertEquals(List.of(
                "t.sprite:9: anim 'walk': no frame 'x' (frames: r)",
                "t.sprite:9: anim 'walk': no frame 'y' (frames: r)"), errors);
    }

    @Test
    void badAnimLines() {
        assertOneError(HEAD + "anim walk 8 loop: r\n", "expected 'anim <name> fps <n> loop|once: <frame>[*<duration>] ...'");
        assertOneError(HEAD + "anim walk fps\n", "expected 'anim <name> fps <n>");
        assertOneError(HEAD + "anim walk fps 0 loop: r\n", "fps must be a number above 0 and at most 120, not '0'");
        assertOneError(HEAD + "anim walk fps fast loop: r\n", "fps must be a number above 0");
        assertOneError(HEAD + "anim walk fps 8 repeat: r\n", "expected 'loop:' or 'once:' after the fps, not 'repeat:'");
        assertOneError(HEAD + "anim walk fps 8 loop:\n", "anim 'walk' has no frames; list them after 'loop:'");
        assertOneError(HEAD + "anim walk fps 8 loop: r*0\n", "'r*0': the duration after '*' must be a number above 0");
        assertOneError(HEAD + "anim walk fps 8 loop: r*x\n", "'r*x': the duration after '*' must be a number above 0");
        assertOneError(HEAD + "anim walk fps 8 loop: r, r\n", "'r,' is not a frame name");
        assertOneError(HEAD + "anim w fps 8 loop: r\nanim w fps 4 loop: r\n", "t.sprite:10: anim 'w' is already defined on line 9");
    }

    @Test
    void anAnimInsideAFrameMeansAMissingEnd() {
        assertOneError("size 1 1\npalette fx\nframe r\nK\nanim a fps 1 loop: r\n", "frame 'r' has no 'end' before line 5");
    }
}
