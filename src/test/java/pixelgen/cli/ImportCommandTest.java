package pixelgen.cli;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pixelgen.model.Sprite;
import pixelgen.parse.PaletteResolver;
import pixelgen.parse.SpriteParser;
import pixelgen.render.Renderer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ImportCommandTest extends CommandTest {
    private Sprite apple;

    @BeforeEach
    void setUp() throws Exception {
        copy("palettes/basic.pal", "palettes/basic.pal");
        apple = SpriteParser.parseFile(RESOURCES.resolve("sprites/apple.sprite"), "apple.sprite",
                PaletteResolver.cached(RESOURCES));
        BufferedImage big = Renderer.render(apple.frame("idle").grid(), apple.palette(), 4);
        big.setRGB(0, 0, 0xFFC12121);  // one off-palette pixel, so the 4x4 blocks are no longer solid
        ImageIO.write(Renderer.render(apple.frame("idle").grid(), apple.palette(), 4), "png",
                project.resolve("My Apple!.png").toFile());
        ImageIO.write(big, "png", project.resolve("noisy.png").toFile());
    }

    @Override
    pixelgen.Cli.Command command() { return new ImportCommand(project); }

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
        write("text.png", "not an image");
        assertEquals(1, run("text.png", "--palette", "basic"));
        assertEquals(1, run("My Apple!.png", "--palette", "basic", "--scale", "3"));
    }

    @Test
    void badNamesAreUsageErrors() throws Exception {
        assertEquals(2, run("My Apple!.png", "--palette", "basic", "--name", "a.b"));
        assertTrue(err().contains("--name 'a.b' isn't a sprite name"), err());
        assertEquals(2, run("My Apple!.png", "--palette", "basic", "--frame", "my frame"));
        assertTrue(err().contains("--frame 'my frame' isn't a frame name"), err());
        assertFalse(err().contains("internal error"), err());
    }

    @Test
    void spriteNamesAreCleanedUp() {
        assertEquals("My_Apple_", ImportCommand.spriteNameFor(Path.of("My Apple!.png")));
        assertEquals("tree-01", ImportCommand.spriteNameFor(Path.of("tree-01.png")));
        assertEquals("imported", ImportCommand.spriteNameFor(Path.of("!!!.png")));
    }
}
