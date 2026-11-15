package pixelgen.cli;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RenderCommandTest {

    @TempDir Path project;

    private final ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errBuf = new ByteArrayOutputStream();

    @BeforeEach
    void setUp() throws Exception {
        Files.createDirectories(project.resolve("palettes"));
        Files.copy(Path.of("src/test/resources/palettes/basic.pal"), project.resolve("palettes/basic.pal"));
        Files.createDirectories(project.resolve("assets"));
        Files.copy(Path.of("src/test/resources/sprites/apple.sprite"), project.resolve("assets/apple.sprite"));
        Files.writeString(project.resolve("assets/two.sprite"),
                "size 2 1\npalette basic\nframe a\nKw\nend\nframe b\nw.\nend\n");
    }

    private int run(String... args) throws Exception {
        return new RenderCommand(project).run(List.of(args),
                new PrintStream(outBuf, true, StandardCharsets.UTF_8),
                new PrintStream(errBuf, true, StandardCharsets.UTF_8));
    }

    private String out() { return outBuf.toString(StandardCharsets.UTF_8); }
    private String err() { return errBuf.toString(StandardCharsets.UTF_8); }

    private BufferedImage read(String path) throws Exception {
        return ImageIO.read(project.resolve(path).toFile());
    }

    @Test
    void writesOnePngPerFrameIntoPreview() throws Exception {
        assertEquals(0, run("assets/two.sprite"));
        assertTrue(out().contains("wrote preview/two/a.png"), out());
        assertTrue(out().contains("wrote preview/two/b.png"), out());
    }

    @Test
    void pngKeepsExactColoursAndTransparency() throws Exception {
        assertEquals(0, run("assets/two.sprite"));
        BufferedImage b = read("preview/two/b.png");
        assertEquals(0xFFFFFFFF, b.getRGB(0, 0));
        assertEquals(0, b.getRGB(1, 0) >>> 24, "transparent pixel survives the PNG round trip");
    }

    @Test
    void scaleOptionEnlargesTheOutput() throws Exception {
        assertEquals(0, run("assets/apple.sprite", "--scale", "8"));
        assertTrue(out().contains("wrote preview/apple/idle.png (128x128)"), out());
        BufferedImage img = read("preview/apple/idle.png");
        assertEquals(128, img.getWidth());
        assertEquals(0xFFFFFFFF, img.getRGB(5 * 8 + 3, 6 * 8 + 3), "highlight pixel (5,6) at x8");
    }

    @Test
    void frameOptionRendersOnlyThatFrame() throws Exception {
        assertEquals(0, run("assets/two.sprite", "--frame", "b"));
        assertTrue(Files.exists(project.resolve("preview/two/b.png")));
        assertFalse(Files.exists(project.resolve("preview/two/a.png")));
    }

    @Test
    void unknownFrameListsTheFramesThatExist() throws Exception {
        assertEquals(1, run("assets/two.sprite", "--frame", "zzz"));
        assertTrue(out().contains("no frame 'zzz' (frames: a, b)"), out());
    }

    @Test
    void outOptionAndFolders() throws Exception {
        assertEquals(0, run("assets", "--out", "elsewhere"));
        assertTrue(Files.exists(project.resolve("elsewhere/apple/idle.png")));
        assertTrue(Files.exists(project.resolve("elsewhere/two/a.png")));
    }

    @Test
    void invalidSpritesAreReportedAndOthersStillRendered() throws Exception {
        Files.writeString(project.resolve("assets/bad.sprite"), "size 1 1\npalette basic\nframe a\nQ\nend\n");
        assertEquals(1, run("assets"));
        assertTrue(out().contains("FAIL assets/bad.sprite"), out());
        assertTrue(Files.exists(project.resolve("preview/apple/idle.png")));
    }

    @Test
    void usageErrors() throws Exception {
        assertEquals(2, run());
        assertEquals(2, run("assets", "--scale", "0"));
        assertEquals(2, run("assets", "--scale", "big"));
        assertEquals(2, run("assets", "--scale"));
        assertEquals(2, run("assets", "--colour", "red"));
        assertEquals(1, run("missing.sprite"));
        assertTrue(err().contains("not found: missing.sprite"), err());
    }
}
