package pixelgen.cli;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

class ReviewCommandTest extends CommandTest {

    @BeforeEach
    void setUp() throws Exception {
        copy("palettes/basic.pal", "palettes/basic.pal");
        copy("sprites/apple.sprite", "assets/apple.sprite");
    }

    @Override
    pixelgen.Cli.Command command() { return new ReviewCommand(project); }

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
        write("assets/bad.sprite", "size 1 1\npalette basic\nframe a\nQ\nend\n");
        assertEquals(1, run("assets"));
        assertTrue(out().contains("FAIL assets/bad.sprite"), out());
        assertTrue(Files.exists(project.resolve("preview/apple/review.png")), "valid sprites are still reviewed");
    }

    @Test
    void aSheetTooLargeFailsThatFileOnly() throws Exception {
        write("assets/huge.sprite", "size 256 256\npalette basic\nframe a\n  stamp dot 0 0\nend\n"
                + "part dot\n  K\nend\n");
        assertEquals(1, run("assets", "--scale", "64"));
        assertTrue(out().contains("FAIL assets/huge.sprite: the image would be "), out());
        assertTrue(out().contains("wrote preview/apple/review.png"), out());
    }

    @Test
    void usageErrors() throws Exception {
        assertEquals(2, run());
        assertEquals(2, run("assets", "--scale", "0"));
        assertEquals(2, run("assets", "--zoom", "2"));
    }

    @Test
    void stripAndOnionWriteTheirOwnSheetsAndSkipSpritesWithoutAnims() throws Exception {
        write("assets/blink.sprite",
                "size 1 1\npalette basic\nframe on\nK\nend\nframe off\nw\nend\nanim blink fps 2 loop: on off\n");
        assertEquals(0, run("assets", "--strip", "--onion"));
        assertTrue(out().contains("wrote preview/blink/strip.png"), out());
        assertTrue(out().contains("wrote preview/blink/onion.png"), out());
        assertTrue(out().contains("skip assets/apple.sprite: no anims"), out());
        assertFalse(Files.exists(project.resolve("preview/blink/review.png")), "only the modes asked for");
    }
}
