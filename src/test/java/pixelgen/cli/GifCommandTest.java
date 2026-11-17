package pixelgen.cli;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

class GifCommandTest extends CommandTest {

    @BeforeEach
    void setUp() throws Exception {
        copy("palettes/basic.pal", "palettes/basic.pal");
        copy("sprites/apple.sprite", "assets/apple.sprite");
        write("assets/blink.sprite",
                "size 2 2\npalette basic\nframe on\nKK\nKK\nend\nframe off\nww\nww\nend\n"
                        + "anim blink fps 2 loop: on off\nanim once fps 10 once: on\nvariant blink_red recolor K>r\n");
    }

    @Override
    pixelgen.Cli.Command command() { return new GifCommand(project); }

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
