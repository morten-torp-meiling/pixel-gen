package pixelgen.parse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pixelgen.model.Grid;
import pixelgen.model.Light;
import pixelgen.model.Sprite;
import pixelgen.model.Style;
import pixelgen.model.Tileset;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The 'style' line: what a sprite or tileset takes from its style, and what it overrides. */
class SpriteStyleTest extends ParserTest {
    /** A 5x4 frame with a 1x2 stick in the middle; the anchor is (2,3). */
    private static final String STICK_FRAME = "frame a\n.....\n..g..\n..g..\n.....\nend\n";

    @TempDir Path project;

    @BeforeEach
    void setUp() throws Exception {
        Files.createDirectories(project.resolve("palettes"));
        Files.createDirectories(project.resolve("styles"));
        Files.copy(RESOURCES.resolve("palettes/basic.pal"), project.resolve("palettes/basic.pal"));
        Files.copy(RESOURCES.resolve("palettes/fx.pal"), project.resolve("palettes/fx.pal"));
        // basic: palette basic, light top-right, outline K selective, shadow k, tile 8 8
        Files.copy(RESOURCES.resolve("styles/basic.style"), project.resolve("styles/basic.style"));
        Files.copy(RESOURCES.resolve("styles/basic.md"), project.resolve("styles/basic.md"));
        style("plain", "palette basic\n");
    }

    private void style(String name, String text) throws Exception {
        Files.writeString(project.resolve("styles/" + name + ".style"), text);
        Files.writeString(project.resolve("styles/" + name + ".md"), "# " + name + "\n");
    }

    private void defaultStyle(String name) throws Exception {
        Files.writeString(project.resolve("styles/styles.conf"), "default = " + name + "\n");
    }

    @Override
    Sprite parse(String text) throws ParseException {
        return SpriteParser.parse(text, "t.sprite", "t", PaletteResolver.cached(project));
    }

    @Test
    void theStyleGivesThePalette() throws Exception {
        Sprite s = parse("size 5 4\nstyle basic\n" + STICK_FRAME);
        assertEquals("basic", s.style().name());
        assertEquals(Light.TOP_RIGHT, s.style().light());
        assertEquals("basic", s.palette().name());
    }

    @Test
    void aPaletteLineOverridesTheStylesPalette() throws Exception {
        Sprite s = parse("size 5 4\npalette fx\nstyle basic\n" + STICK_FRAME);
        assertEquals("basic", s.style().name());
        assertEquals("fx", s.palette().name());
    }

    @Test
    void filesWithoutAStyleLineGetTheDefaultStyle() throws Exception {
        defaultStyle("basic");
        Sprite s = parse("size 5 4\n" + STICK_FRAME);
        assertEquals("basic", s.style().name());
        assertEquals("basic", s.palette().name());
        assertEquals("plain", parse("size 5 4\nstyle plain\n" + STICK_FRAME).style().name());
    }

    @Test
    void withoutADefaultStyleFilesGetTheBuiltInOneAndNeedAPalette() throws Exception {
        Sprite s = parse("size 5 4\npalette basic\n" + STICK_FRAME);
        assertSame(Style.BUILT_IN, s.style());
        assertEquals(List.of("t.sprite: missing 'palette <name>' (or a 'style <name>' whose palette it uses)"),
                errorsOf("size 5 4\n" + STICK_FRAME));
    }

    @Test
    void bareOutlineTakesTheStylesColourAndMode() throws Exception {
        Sprite bare = parse("size 5 4\nstyle basic\noutline\n" + STICK_FRAME);
        Sprite full = parse("size 5 4\nstyle basic\noutline K selective\n" + STICK_FRAME);
        assertEquals(full.frame("a").grid(), bare.frame("a").grid());
        assertNotEquals(parse("size 5 4\nstyle basic\noutline K\n" + STICK_FRAME).frame("a").grid(),
                bare.frame("a").grid(), "selective differs from outer here");
    }

    @Test
    void aModeWithoutAColourOverridesTheStylesMode() throws Exception {
        Sprite outer = parse("size 5 4\nstyle basic\noutline outer\n" + STICK_FRAME);
        assertEquals(parse("size 5 4\nstyle basic\noutline K outer\n" + STICK_FRAME).frame("a").grid(),
                outer.frame("a").grid());
    }

    @Test
    void bareFormsWorkInsideFramesToo() throws Exception {
        String inFrame = "frame a\n.....\n..g..\n..g..\n.....\noutline\nshadow 3 1\nend\n";
        String full = "frame a\n.....\n..g..\n..g..\n.....\noutline K selective\nshadow k 3 1\nend\n";
        assertEquals(parse("size 5 4\nanchor 2 3\nstyle basic\n" + full).frame("a").grid(),
                parse("size 5 4\nanchor 2 3\nstyle basic\n" + inFrame).frame("a").grid());
    }

    @Test
    void bareShadowTakesTheStylesColour() throws Exception {
        Sprite bare = parse("size 5 4\nanchor 2 3\nstyle basic\nshadow 3 1\n" + STICK_FRAME);
        Sprite full = parse("size 5 4\nanchor 2 3\nstyle basic\nshadow k 3 1\n" + STICK_FRAME);
        assertEquals(full.frame("a").grid(), bare.frame("a").grid());
        assertEquals('k', bare.frame("a").grid().at(2, 3));
    }

    @Test
    void bareFormsNeedAStyleThatSetsThem() {
        assertEquals(List.of(
                "t.sprite:3: 'outline' without a colour takes it from the style, and style 'plain' sets none; "
                        + "write 'outline <colour> [outer|selective]', like 'outline K'",
                "t.sprite:4: 'shadow <width> <height>' takes its colour from the style, and style 'plain' sets none; "
                        + "write 'shadow <colour> <width> <height>', like 'shadow ~ 12 3'"),
                errorsOf("size 5 4\nstyle plain\noutline\nshadow 3 1\n" + STICK_FRAME));
    }

    @Test
    void theStylesColourMustBeInAnOverridingPalette() throws Exception {
        style("tilde", "palette fx\nshadow ~\n");
        assertEquals(List.of("t.sprite:4: '~' is not in palette 'basic'"),
                errorsOf("size 5 4\npalette basic\nstyle tilde\nshadow 3 1\n" + STICK_FRAME));
    }

    @Test
    void styleErrors() throws Exception {
        List<String> e = errorsOf("size 5 4\nstyle nowhere\n" + STICK_FRAME);
        assertEquals(1, e.size(), e.toString());
        assertTrue(e.getFirst().startsWith("t.sprite:2: style 'nowhere' not found ("), e.getFirst());

        style("broken", "palette basic\nlight up\n");
        e = errorsOf("size 5 4\nstyle broken\noutline\n" + STICK_FRAME);
        assertEquals(1, e.size(), "no follow-on errors about the palette or the bare outline: " + e);
        assertEquals("t.sprite:2: style 'broken' has errors:\n  styles/broken.style:2: expected 'light <direction>', "
                + "with top-left, top-right, bottom-left or bottom-right", e.getFirst());

        assertEquals(List.of("t.sprite:3: 'style' is already set on line 2"),
                errorsOf("size 5 4\nstyle basic\nstyle plain\n" + STICK_FRAME));
        assertEquals(List.of("t.sprite:2: expected 'style <name or file.style>'"),
                errorsOf("size 5 4\nstyle\n" + STICK_FRAME));
    }

    @Test
    void aBrokenDefaultStyleIsReported() throws Exception {
        defaultStyle("gone");
        List<String> e = errorsOf("size 5 4\npalette basic\n" + STICK_FRAME);
        assertEquals(1, e.size(), e.toString());
        assertTrue(e.getFirst().startsWith("t.sprite: the default style (set in styles/styles.conf) not found ("),
                e.getFirst());
    }

    @Test
    void tilesetsTakeTheStylesTileSize() throws Exception {
        String tile = "tile a\n" + "gggggggg\n".repeat(8) + "end\n";
        Tileset t = TilesetParser.parse("style basic\n" + tile, "t.tileset", "t", PaletteResolver.cached(project));
        assertEquals(8, t.sprite().width());
        assertEquals(8, t.sprite().height());
        assertEquals("basic", t.sprite().style().name());

        String big = "tile a\n" + "gggggggggggggggg\n".repeat(16) + "end\n";
        Tileset plain = TilesetParser.parse("style plain\n" + big, "t.tileset", "t", PaletteResolver.cached(project));
        assertEquals(16, plain.sprite().width(), "a style without 'tile' gives 16x16");

        List<String> e = assertThrows(ParseException.class, () -> TilesetParser.parse("style basic\nsize 4 4\n" + tile,
                "t.tileset", "t", PaletteResolver.cached(project))).errors();
        assertTrue(e.getFirst().contains("tile 'a'"), "a size line wins over the style: " + e);
    }

    @Test
    void theStylesLightReachesOutlines() throws Exception {
        style("left", "palette basic\noutline K selective\n");
        // A one-pixel 'g' in a 3x3 frame: the lit sides get G (green's darkest shade), the others K.
        String dot = "size 3 3\noutline K selective\nframe a\n...\n.g.\n...\nend\n";
        assertEquals(Grid.of(".G.", "KgG", ".K."), parse("style basic\n" + dot).frame("a").grid(), "top right");
        assertEquals(Grid.of(".G.", "GgK", ".K."), parse("style left\n" + dot).frame("a").grid(), "top left");
        assertEquals(Grid.of(".G.", "KgG", ".K."), parse("style basic\n" + dot.replace("outline K selective\n", "")
                .replace("...\nend", "...\noutline\nend")).frame("a").grid(), "a bare outline inside a frame");
    }

    @Test
    void theStylesLightReachesTerrainShading() throws Exception {
        style("left", "palette basic\ntile 4 4\n");
        Files.writeString(project.resolve("styles/basic.style"),
                Files.readString(project.resolve("styles/basic.style")).replace("tile 8 8", "tile 4 4"));
        // Raised 'g' on the left two columns; bank G, lip y, shadow k.
        String tiles = """
                part raised
                gggg
                gggg
                gggg
                gggg
                end
                part lower
                wwww
                wwww
                wwww
                wwww
                end
                terrain t
                  raised raised
                  lower lower
                  bank G
                  lip y
                  shadow k
                  w 2 2 2 2
                end
                tile edge wrap y
                  blend t w
                end
                """;
        PaletteResolver r = PaletteResolver.cached(project);
        assertEquals(Grid.of("gGkw", "gGkw", "gGkw", "gGkw"),
                TilesetParser.parse("style left\n" + tiles, "t.tileset", "t", r).sprite().frame("edge").grid());
        assertEquals(Grid.of("gyww", "gyww", "gyww", "gyww"),
                TilesetParser.parse("style basic\n" + tiles, "t.tileset", "t", r).sprite().frame("edge").grid());
    }

    @Test
    void variantsKeepTheStyle() throws Exception {
        Sprite s = parse("size 5 4\nstyle basic\n" + STICK_FRAME + "variant red recolor g>r\n");
        assertEquals("basic", s.variant("red").style().name());
    }
}
