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

class ReviewCommandTest {

    @TempDir Path project;

    private final ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errBuf = new ByteArrayOutputStream();

    @BeforeEach
    void setUp() throws Exception {
        Files.createDirectories(project.resolve("palettes"));
        Files.copy(Path.of("src/test/resources/palettes/basic.pal"), project.resolve("palettes/basic.pal"));
        Files.createDirectories(project.resolve("assets"));
        Files.copy(Path.of("src/test/resources/sprites/apple.sprite"), project.resolve("assets/apple.sprite"));
    }

    private int run(String... args) throws Exception {
        return new ReviewCommand(project).run(List.of(args),
                new PrintStream(outBuf, true, StandardCharsets.UTF_8),
                new PrintStream(errBuf, true, StandardCharsets.UTF_8));
    }

    private String out() { return outBuf.toString(StandardCharsets.UTF_8); }

    @Test
    void writesReviewSheetWithAutomaticScale() throws Exception {
        assertEquals(0, run("assets/apple.sprite"));
        assertTrue(out().contains("wrote preview/apple/review.png (scale 16x, "), out());
        BufferedImage img = ImageIO.read(project.resolve("preview/apple/review.png").toFile());
        assertTrue(img.getWidth() > 256 && img.getHeight() > 256);
    }

    @Test
    void scaleAndOutOptions() throws Exception {
        assertEquals(0, run("assets", "--scale", "4", "--out", "sheets"));
        assertTrue(out().contains("wrote sheets/apple/review.png (scale 4x, "), out());
    }

    @Test
    void invalidSpriteFails() throws Exception {
        Files.writeString(project.resolve("assets/bad.sprite"), "size 1 1\npalette basic\nframe a\nQ\nend\n");
        assertEquals(1, run("assets"));
        assertTrue(out().contains("FAIL assets/bad.sprite"), out());
        assertTrue(Files.exists(project.resolve("preview/apple/review.png")), "valid sprites are still reviewed");
    }

    @Test
    void usageErrors() throws Exception {
        assertEquals(2, run());
        assertEquals(2, run("assets", "--scale", "0"));
        assertEquals(2, run("assets", "--zoom", "2"));
    }
}
