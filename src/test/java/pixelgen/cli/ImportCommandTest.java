package pixelgen.cli;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pixelgen.model.Sprite;
import pixelgen.parse.PaletteResolver;
import pixelgen.parse.SpriteParser;
import pixelgen.render.Renderer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ImportCommandTest {

    @TempDir Path project;

    private final ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
    private Sprite apple;

    @BeforeEach
    void setUp() throws Exception {
        Path res = Path.of("src/test/resources");
        Files.createDirectories(project.resolve("palettes"));
        Files.copy(res.resolve("palettes/basic.pal"), project.resolve("palettes/basic.pal"));
        apple = SpriteParser.parseFile(res.resolve("sprites/apple.sprite"), "apple.sprite", PaletteResolver.cached(res));
        BufferedImage big = Renderer.render(apple.frame("idle").grid(), apple.palette(), 4);
        big.setRGB(0, 0, 0xFFC12121);  // one off-palette pixel, so the 4x4 blocks are no longer solid
        ImageIO.write(Renderer.render(apple.frame("idle").grid(), apple.palette(), 4), "png",
                project.resolve("My Apple!.png").toFile());
        ImageIO.write(big, "png", project.resolve("noisy.png").toFile());
    }

    private int run(String... args) throws Exception {
        return new ImportCommand(project).run(List.of(args),
                new PrintStream(outBuf, true, StandardCharsets.UTF_8),
                new PrintStream(errBuf, true, StandardCharsets.UTF_8));
    }

    private String out() { return outBuf.toString(StandardCharsets.UTF_8); }
    private String err() { return errBuf.toString(StandardCharsets.UTF_8); }

    @Test
    void printsSpriteTextToStdoutAndReportToStderr() throws Exception {
        assertEquals(0, run("My Apple!.png", "--palette", "basic"));
        assertTrue(out().startsWith("# Imported from My Apple!.png\nsprite My_Apple_\nsize 16 16\npalette basic\n"), out());
        assertTrue(err().contains("image 64x64 -> 16x16 (scale 4)"), err());
        assertTrue(err().contains("colours: 6 exact, 0 replaced"), err());
        Sprite back = SpriteParser.parse(out(), "x", "x", n -> apple.palette());
        assertEquals(apple.frame("idle").grid(), back.frame("idle").grid());
    }

    @Test
    void outWritesAFileAndRefusesToOverwrite() throws Exception {
        assertEquals(0, run("My Apple!.png", "--palette", "basic", "--name", "apple", "--frame", "ripe",
                "--out", "assets/apple.sprite"));
        assertTrue(out().contains("wrote assets/apple.sprite"), out());
        assertTrue(out().contains("colours: 6 exact"), out());
        String text = Files.readString(project.resolve("assets/apple.sprite"));
        assertTrue(text.contains("sprite apple\n") && text.contains("frame ripe\n"), text);

        assertEquals(1, run("My Apple!.png", "--palette", "basic", "--out", "assets/apple.sprite"));
        assertTrue(err().contains("--force"), err());
        assertEquals(0, run("My Apple!.png", "--palette", "basic", "--out", "assets/apple.sprite", "--force"));
    }

    @Test
    void reportsReplacedColoursWhenScaleIsGiven() throws Exception {
        assertEquals(0, run("noisy.png", "--palette", "basic", "--scale", "4"));
        assertTrue(err().contains("colours: 6 exact, 1 replaced"), err());
        assertTrue(err().contains("#c12121 (1 px) -> 'r' #c02020  distance 0.00"), err());
    }

    @Test
    void anOffPixelStopsScaleDetection() throws Exception {
        // One pixel breaks the solid 4x4 blocks, so the image is imported at 1:1 unless --scale is given.
        assertEquals(0, run("noisy.png", "--palette", "basic"));
        assertTrue(err().contains("image 64x64 -> 64x64 (scale 1)"), err());
    }

    @Test
    void errors() throws Exception {
        assertEquals(2, run("My Apple!.png"));
        assertEquals(2, run("--palette", "basic"));
        assertEquals(2, run("My Apple!.png", "--palette", "basic", "--scale", "x"));
        assertEquals(1, run("missing.png", "--palette", "basic"));
        assertTrue(err().contains("not found: missing.png"), err());
        Files.writeString(project.resolve("text.png"), "not an image");
        assertEquals(1, run("text.png", "--palette", "basic"));
        assertEquals(1, run("My Apple!.png", "--palette", "basic", "--scale", "3"));
    }

    @Test
    void spriteNamesAreCleanedUp() {
        assertEquals("My_Apple_", ImportCommand.spriteNameFor(Path.of("My Apple!.png")));
        assertEquals("tree-01", ImportCommand.spriteNameFor(Path.of("tree-01.png")));
        assertEquals("imported", ImportCommand.spriteNameFor(Path.of("!!!.png")));
    }
}
