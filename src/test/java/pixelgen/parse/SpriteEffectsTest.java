package pixelgen.parse;

import org.junit.jupiter.api.Test;
import pixelgen.model.Grid;
import pixelgen.model.Sprite;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** outline and shadow lines in sprite files, inside frames and at sprite level (fx palette). */
class SpriteEffectsTest extends ParserTest {

    private static final PaletteResolver FX = only("fx");
    // 5x4, anchor (2,3): a 1x2 stick in the middle.
    private static final String HEAD = "size 5 4\npalette fx\nanchor 2 3\n";
    private static final String STICK = ".....\n..g..\n..g..\n.....\n";

    @Override
    Sprite parse(String text) throws ParseException {
        return SpriteParser.parse(text, "t.sprite", "t", FX);
    }

    @Test
    void outlineInsideAFrame() throws Exception {
        Sprite s = parse(HEAD + "frame a\n" + STICK + "outline K\nend\n");
        assertEquals(Grid.of("..K..", ".KgK.", ".KgK.", "..K.."), s.frame("a").grid());
        assertEquals(List.of(), s.warnings());
    }

    @Test
    void selectiveOutline() throws Exception {
        Sprite s = parse(HEAD + "frame a\n" + STICK + "outline K selective\nend\n");
        assertEquals(Grid.of("..G..", ".GgK.", ".GgK.", "..K.."), s.frame("a").grid());
    }

    @Test
    void frameLinesRunInOrderSoAStampAfterTheOutlineIsNotOutlined() throws Exception {
        String text = HEAD + "part dot\nr\nend\nframe a\n" + STICK + "outline K\nstamp dot 4 0\nend\n";
        assertEquals(Grid.of("..K.r", ".KgK.", ".KgK.", "..K.."), parse(text).frame("a").grid());
    }

    @Test
    void shadowSitsOnTheAnchorBehindTheArt() throws Exception {
        Sprite s = parse(HEAD + "frame a\n.....\n.....\n..g..\n..g..\nshadow ~ 3 1\nend\n");
        assertEquals(Grid.of(".....", ".....", "..g..", ".~g~."), s.frame("a").grid());
    }

    @Test
    void spriteLevelEffectsApplyToEveryFrameAfterItsOwnLines() throws Exception {
        String text = HEAD + "outline K\nshadow ~ 5 1\n"
                + "frame a\n" + STICK + "end\n"
                + "frame b\n" + STICK + "recolor g>r\nend\n";
        Sprite s = parse(text);
        // The outline comes first, so the shadow only fills what is still empty on the anchor row.
        assertEquals(Grid.of("..K..", ".KgK.", ".KgK.", "~~K~~"), s.frame("a").grid());
        assertEquals(Grid.of("..K..", ".KrK.", ".KrK.", "~~K~~"), s.frame("b").grid());
    }

    @Test
    void variantsRecolourTheOutlineToo() throws Exception {
        Sprite s = parse(HEAD + "outline K\nframe a\n" + STICK + "end\nvariant t_soft recolor K>k\n");
        assertEquals(Grid.of("..k..", ".kgk.", ".kgk.", "..k.."), s.variant("t_soft").frame("a").grid());
    }

    @Test
    void cutOffEffectsAreWarnings() throws Exception {
        Sprite s = parse(HEAD + "frame a\n..g..\n.....\n.....\n.....\noutline K\nend\n");
        assertEquals(1, s.warnings().size(), s.warnings().toString());
        assertTrue(s.warnings().getFirst().contains("t.sprite:9: frame 'a': the outline is cut off at the frame edge"),
                s.warnings().getFirst());

        String twoFrames = HEAD + "shadow ~ 9 1\nframe a\n" + STICK + "end\nframe b\n" + STICK + "end\n";
        List<String> w = parse(twoFrames).warnings();
        assertEquals(List.of("t.sprite:4: the 9x1 shadow is cut off at the frame edge in frames a, b"), w);
    }

    @Test
    void effectColoursMustBeInThePalette() {
        assertOneError(HEAD + "frame a\n" + STICK + "outline Q\nend\n", "t.sprite:9: frame 'a': 'Q' is not in palette 'fx'");
        assertOneError(HEAD + "shadow Q 3 1\nframe a\n" + STICK + "end\n", "t.sprite:4: 'Q' is not in palette 'fx'");
    }

    @Test
    void badEffectSyntax() {
        String frame = "frame a\n" + STICK + "end\n";
        assertOneError(HEAD + "outline\n" + frame, "'outline' without a colour takes it from the style, and the "
                + "built-in style");
        assertOneError(HEAD + "outline KK\n" + frame, "expected 'outline <colour> [outer|selective]'");
        assertOneError(HEAD + "outline . outer\n" + frame, "expected 'outline <colour> [outer|selective]'");
        assertOneError(HEAD + "outline K inner\n" + frame, "unknown outline mode 'inner'; expected outer or selective");
        assertOneError(HEAD + "shadow ~ 3\n" + frame, "expected 'shadow <colour> <width> <height>'");
        assertOneError(HEAD + "shadow ~ wide 1\n" + frame, "'wide' is not a whole number");
        assertOneError(HEAD + "shadow ~ 0 1\n" + frame, "shadow size must be between 1 and 256");
        assertOneError(HEAD + "shadow 3 300\n" + frame, "shadow size must be between 1 and 256");
        assertOneError(HEAD + "frame a\n" + STICK + "shadow\nend\n", "expected 'shadow <colour> <width> <height>'");
    }

    @Test
    void partsCannotHaveEffectsAndRowsComeFirst() {
        assertOneError(HEAD + "part p\ng\noutline K\nend\nframe a\nstamp p 0 0\nend\n",
                "part 'p': parts can't contain 'outline' lines");
        assertOneError(HEAD + "frame a\nshadow ~ 3 1\n" + STICK + "end\n",
                "grid rows must come before 'stamp', 'recolor', 'outline' and 'shadow' lines");
    }
}
