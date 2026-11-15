package pixelgen.cli;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GifCommandTest {

    @TempDir Path project;

    private final ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errBuf = new ByteArrayOutputStream();

    @BeforeEach
    void setUp() throws Exception {
        Files.createDirectories(project.resolve("palettes"));
        Files.copy(Path.of("src/test/resources/palettes/basic.pal"), project.resolve("palettes/basic.pal"));
        Files.createDirectories(project.resolve("assets"));
        Files.copy(Path.of("src/test/resources/sprites/apple.sprite"), project.resolve("assets/apple.sprite"));
        Files.writeString(project.resolve("assets/blink.sprite"),
                "size 2 2\npalette basic\nframe on\nKK\nKK\nend\nframe off\nww\nww\nend\n"
                        + "anim blink fps 2 loop: on off\nanim once fps 10 once: on\nvariant blink_red recolor K>r\n");
    }

    private int run(String... args) throws Exception {
        return new GifCommand(project).run(List.of(args),
                new PrintStream(outBuf, true, StandardCharsets.UTF_8),
                new PrintStream(errBuf, true, StandardCharsets.UTF_8));
    }

    private String out() { return outBuf.toString(StandardCharsets.UTF_8); }
    private String err() { return errBuf.toString(StandardCharsets.UTF_8); }

    @Test
    void writesEveryAnimForTheSpriteAndItsVariants() throws Exception {
        assertEquals(0, run("assets"));
        assertTrue(out().contains("wrote preview/blink/blink.gif (2 frames, 2 fps, loop, 1.00 s)"), out());
        assertTrue(out().contains("wrote preview/blink/once.gif (1 frames, 10 fps, once, 0.10 s)"), out());
        assertTrue(out().contains("wrote preview/blink_red/blink.gif"), out());
        assertTrue(out().contains("skip assets/apple.sprite: no anims"), out());
        var gif = ImageIO.read(project.resolve("preview/blink/blink.gif").toFile());
        assertEquals(8, gif.getWidth(), "default scale 4");
        assertEquals(0xFF000000, gif.getRGB(1, 1));
    }

    @Test
    void animScaleBackgroundAndOutOptions() throws Exception {
        assertEquals(0, run("assets/blink.sprite", "--anim", "once", "--scale", "1", "--bg", "none", "--out", "gifs"));
        assertTrue(Files.exists(project.resolve("gifs/blink/once.gif")));
        assertFalse(Files.exists(project.resolve("gifs/blink/blink.gif")));
        assertEquals(2, ImageIO.read(project.resolve("gifs/blink/once.gif").toFile()).getWidth());
    }

    @Test
    void unknownAnimListsTheAnimsThatExist() throws Exception {
        assertEquals(1, run("assets/blink.sprite", "--anim", "walk"));
        assertTrue(out().contains("no anim 'walk' (anims: blink, once)"), out());
    }

    @Test
    void nothingToWriteIsAFailure() throws Exception {
        assertEquals(1, run("assets/apple.sprite"));
        assertTrue(err().contains("no anims found"), err());
    }

    @Test
    void usageErrors() throws Exception {
        assertEquals(2, run());
        assertEquals(2, run("assets", "--bg", "green"));
        assertEquals(2, run("assets", "--scale", "0"));
        assertEquals(2, run("assets", "--fps", "8"));
        assertEquals(2, run("assets", "--anim"));
    }
}
