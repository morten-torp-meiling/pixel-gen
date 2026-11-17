package pixelgen.model;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pixelgen.parse.Palettes;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static pixelgen.model.Effects.OutlineMode.OUTER;
import static pixelgen.model.Effects.OutlineMode.SELECTIVE;

/** Outline and shadow on small grids (fx palette: K k w | G g | y r unordered | ~ translucent). */
class EffectsTest {

    private static Palette fx;

    @BeforeAll
    static void load() throws Exception {
        fx = Palettes.load("fx", Path.of("src/test/resources"));
    }

    private static final Grid BUMP = Grid.of(
            ".....",
            "..g..",
            ".ggg.",
            ".....");

    // ---- outline ----

    @Test
    void outerOutlineGoesOnFourSidesButNotDiagonally() {
        Effects.Result r = Effects.outline(BUMP, fx, 'K', OUTER);
        assertEquals(Grid.of(
                "..K..",
                ".KgK.",
                "KgggK",
                ".KKK."), r.grid());
        assertFalse(r.clipped());
    }

    @Test
    void selectiveOutlineUsesTheDarkestShadeOnTheLitTopAndLeft() {
        // Top and left edges border g, whose ramp's darkest shade is G; right and bottom edges stay K.
        assertEquals(Grid.of(
                "..G..",
                ".GgG.",
                "GgggK",
                ".KKK."), Effects.outline(BUMP, fx, 'K', SELECTIVE).grid());
    }

    @Test
    void selectiveOutlineFromTheTopRightDarkensTheTopAndRight() {
        assertEquals(Grid.of(
                "..G..",
                ".GgG.",
                "KgggG",
                ".KKK."), Effects.outline(BUMP, fx, 'K', SELECTIVE, Light.TOP_RIGHT).grid());
        assertEquals(Grid.of(".K.", "KgG", ".G."),
                Effects.outline(Grid.of("...", ".g.", "..."), fx, 'K', SELECTIVE, Light.BOTTOM_RIGHT).grid());
    }

    /** Every light gives the top-left outline of the mirrored shape, mirrored back; outer outlines ignore it. */
    @Test
    void everyLightMirrorsTheTopLeftOutline() {
        Grid shape = Grid.of(
                "........",
                "..gg....",
                ".gGgy...",
                ".ggrgg..",
                "..gwg...",
                "........");
        Grid outer = Effects.outline(shape, fx, 'K', OUTER).grid();
        assertEquals(Effects.outline(shape, fx, 'K', SELECTIVE).grid(),
                Effects.outline(shape, fx, 'K', SELECTIVE, Light.TOP_LEFT).grid());
        for (Light light : Light.values()) {
            boolean flipX = light.lx() > 0, flipY = light.ly() > 0;
            Grid mirrored = shape;
            if (flipX) mirrored = mirrored.flipX();
            if (flipY) mirrored = mirrored.flipY();
            Grid expected = Effects.outline(mirrored, fx, 'K', SELECTIVE).grid();
            if (flipX) expected = expected.flipX();
            if (flipY) expected = expected.flipY();
            assertEquals(expected, Effects.outline(shape, fx, 'K', SELECTIVE, light).grid(), light.word());
            assertEquals(outer, Effects.outline(shape, fx, 'K', OUTER, light).grid(), light.word());
        }
    }

    @Test
    void selectiveFallsBackToTheOutlineColourWhenThereIsNoDarkerShade() {
        // G is already the darkest green; r is in an unordered ramp.
        assertEquals(Grid.of(".K.", "KGK", ".K."), Effects.outline(Grid.of("...", ".G.", "..."), fx, 'K', SELECTIVE).grid());
        assertEquals(Grid.of(".K.", "KrK", ".K."), Effects.outline(Grid.of("...", ".r.", "..."), fx, 'K', SELECTIVE).grid());
    }

    @Test
    void outlineNeverCoversArtButReplacesTranslucentPixels() {
        // The shadow '~' isn't outlined itself, but outline pixels may land on it.
        assertEquals(Grid.of("~K~", "KgK", ".K."), Effects.outline(Grid.of("~~~", "~g~", "..."), fx, 'K', OUTER).grid());
        assertEquals(Grid.of("KK", "KK"), Effects.outline(Grid.of("KK", "KK"), fx, 'k', OUTER).grid());
    }

    @Test
    void artTouchingTheEdgeIsReportedAsClipped() {
        Effects.Result r = Effects.outline(Grid.of("g..", "..."), fx, 'K', OUTER);
        assertEquals(Grid.of("gK.", "K.."), r.grid());
        assertTrue(r.clipped());
    }

    // ---- shadow ----

    @Test
    void shadowIsAnEllipseWithItsBottomRowOnTheAnchor() {
        Effects.Result r = Effects.shadow(Grid.blank(16, 4), '~', 12, 3, 8, 3);
        assertEquals(Grid.of(
                "................",
                "....~~~~~~~~....",
                "..~~~~~~~~~~~~..",
                "....~~~~~~~~...."), r.grid());
        assertFalse(r.clipped());
    }

    @Test
    void oddWidthsAreCentredOnTheAnchorColumn() {
        assertEquals(Grid.of(".~~~~~."), Effects.shadow(Grid.blank(7, 1), '~', 5, 1, 3, 0).grid());
        assertEquals(Grid.of("..~~..", ".~~~~.", "..~~.."), Effects.shadow(Grid.blank(6, 3), '~', 4, 3, 3, 2).grid());
    }

    @Test
    void shadowOnlyFillsEmptyCells() {
        assertEquals(Grid.of("~KK~"), Effects.shadow(Grid.of(".KK."), '~', 4, 1, 2, 0).grid());
    }

    @Test
    void shadowLeavingTheFrameIsReportedAsClipped() {
        Effects.Result r = Effects.shadow(Grid.blank(4, 2), '~', 6, 2, 1, 1);
        assertTrue(r.clipped());
        assertEquals(Grid.of("~~~~", "~~~~"), r.grid());  // columns -2 and -1 are cut off
    }
}
