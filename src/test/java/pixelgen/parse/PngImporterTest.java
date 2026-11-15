package pixelgen.parse;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pixelgen.model.Grid;
import pixelgen.model.Palette;
import pixelgen.model.Sprite;
import pixelgen.render.Renderer;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PngImporterTest {

    private static final Path RESOURCES = Path.of("src/test/resources");
    private static Sprite apple;
    private static Palette basic;

    @BeforeAll
    static void load() throws Exception {
        apple = SpriteParser.parseFile(RESOURCES.resolve("sprites/apple.sprite"), "apple.sprite",
                PaletteResolver.cached(RESOURCES));
        basic = apple.palette();
    }

    private static Grid appleGrid() { return apple.frame("idle").grid(); }

    private static BufferedImage image(int w, int h, int... argb) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, w, h, argb, 0, w);
        return img;
    }

    @Test
    void renderThenImportGivesTheOriginalRows() throws Exception {
        PngImporter.Result r = PngImporter.importImage(Renderer.render(appleGrid(), basic), basic);
        assertEquals(appleGrid(), r.grid());
        assertEquals(1, r.scale());
        assertEquals(List.of(), r.mismatches());

        // The rows in the generated text are character-for-character the rows in the source file.
        String text = PngImporter.toSpriteText("apple", "basic", "idle", r.grid(), "apple.png");
        List<String> sourceRows = Files.readAllLines(RESOURCES.resolve("sprites/apple.sprite")).stream()
                .map(String::strip).filter(l -> l.length() == 16).toList();
        List<String> importedRows = text.lines().map(String::strip).filter(l -> l.length() == 16).toList();
        assertEquals(sourceRows, importedRows);
    }

    @Test
    void generatedTextParsesBackToTheSameFrame() throws Exception {
        PngImporter.Result r = PngImporter.importImage(Renderer.render(appleGrid(), basic), basic);
        String text = PngImporter.toSpriteText("apple", "basic", "idle", r.grid(), "apple.png");
        Sprite back = SpriteParser.parse(text, "x.sprite", "x", n -> basic);
        assertEquals("apple", back.name());
        assertEquals(appleGrid(), back.frame("idle").grid());
    }

    @Test
    void enlargedImagesAreDetectedAndShrunk() {
        PngImporter.Result r = PngImporter.importImage(Renderer.render(appleGrid(), basic, 8), basic);
        assertEquals(8, r.scale());
        assertEquals(appleGrid(), r.grid());
    }

    @Test
    void explicitScaleMustDivideTheImage() {
        BufferedImage img = Renderer.render(appleGrid(), basic);
        assertThrows(IllegalArgumentException.class, () -> PngImporter.importImage(img, basic, 3));
    }

    @Test
    void detectScaleIsOneForNormalImages() {
        assertEquals(1, PngImporter.detectScale(Renderer.render(appleGrid(), basic)));
        assertEquals(1, PngImporter.detectScale(image(1, 1, 0xFF000000)));
    }

    @Test
    void fullyTransparentPixelsCountAsOneValueWhenDetectingScale() {
        // 2x2 blocks: left block is transparent but with different hidden colours.
        BufferedImage img = image(4, 2,
                0x00FF0000, 0x0000FF00, 0xFF000000, 0xFF000000,
                0x00123456, 0x00000000, 0xFF000000, 0xFF000000);
        assertEquals(2, PngImporter.detectScale(img));
        assertEquals(Grid.of(".K"), PngImporter.importImage(img, basic).grid());
    }

    @Test
    void nearColoursMapToTheClosestSymbolAndAreReported() {
        BufferedImage img = image(3, 1, 0xFFC12121, 0xFFC12121, 0xFF000000);
        PngImporter.Result r = PngImporter.importImage(img, basic);
        assertEquals(Grid.of("rrK"), r.grid());
        assertEquals(1, r.exactColours());
        PngImporter.Mismatch m = r.mismatches().getFirst();
        assertEquals(0xC12121, m.rgb());
        assertEquals(2, m.pixels());
        assertEquals('r', m.symbol());
        assertFalse(m.poor(), "distance " + m.distance());
    }

    @Test
    void farColoursAreFlaggedAsPoorMatches() {
        PngImporter.Result r = PngImporter.importImage(image(1, 1, 0xFF0000FF), basic);  // pure blue
        assertTrue(r.mismatches().getFirst().poor(), "distance " + r.mismatches().getFirst().distance());
    }

    @Test
    void mismatchesAreSortedByPixelCount() {
        BufferedImage img = image(4, 1, 0xFF0000FF, 0xFFC12121, 0xFFC12121, 0xFFC12121);
        List<PngImporter.Mismatch> ms = PngImporter.importImage(img, basic).mismatches();
        assertEquals(0xC12121, ms.getFirst().rgb());
        assertEquals(0x0000FF, ms.get(1).rgb());
    }

    @Test
    void alphaBelowHalfIsTransparentAndPartialAlphaIsCounted() {
        BufferedImage img = image(3, 1, 0x7F000000, 0x80000000, 0xFF000000);
        PngImporter.Result r = PngImporter.importImage(img, basic);
        assertEquals(Grid.of(".KK"), r.grid());
        assertEquals(1, r.semiTransparent());
    }

    @Test
    void exactMatchWinsOverNearestSearch() {
        assertEquals('k', PngImporter.nearest(0x555555, basic).symbol());
    }

    @Test
    void translucentPaletteColoursSurviveARoundTrip() throws Exception {
        Palette fx = Palettes.load("fx", RESOURCES);
        Grid g = Grid.of("..~~..", ".~KK~.", "..~~..");
        PngImporter.Result r = PngImporter.importImage(Renderer.render(g, fx, 3), fx);
        assertEquals(g, r.grid());
        assertEquals(List.of(), r.mismatches());
        assertEquals(0, r.semiTransparent());
    }

    @Test
    void withTranslucentColoursOpacityIsMatchedToo() throws Exception {
        Palette fx = Palettes.load("fx", RESOURCES);
        // a 30% dark grey becomes the 33% shadow, a 2% pixel becomes nothing, an opaque one stays opaque
        BufferedImage img = image(3, 1, 0x4D101010, 0x05000000, 0xFF010101);
        PngImporter.Result r = PngImporter.importImage(img, fx);
        assertEquals(Grid.of("~.K"), r.grid());
        assertEquals(3, r.mismatches().size());
        assertTrue(r.mismatches().stream().anyMatch(m -> m.symbol() == '.' && m.alpha() == 5));
    }
}
