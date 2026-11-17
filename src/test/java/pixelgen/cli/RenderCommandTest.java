package pixelgen.cli;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

class RenderCommandTest extends CommandTest {

    @BeforeEach
    void setUp() throws Exception {
        copy("palettes/basic.pal", "palettes/basic.pal");
        copy("sprites/apple.sprite", "assets/apple.sprite");
        write("assets/two.sprite",
                "size 2 1\npalette basic\nframe a\nKw\nend\nframe b\nw.\nend\n");
    }

    @Override
    pixelgen.Cli.Command command() { return new RenderCommand(project); }

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
    void twoFilesWithOneNameAreWarnedAbout() throws Exception {
        Files.createDirectories(project.resolve("assets/other"));
        write("assets/other/two.sprite", "size 1 1\npalette basic\nframe a\nK\nend\n");
        assertEquals(0, run("assets"));
        assertTrue(out().contains("warning: assets/other/two.sprite also writes images as 'two'; this file replaces them"),
                out());
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
        write("assets/bad.sprite", "size 1 1\npalette basic\nframe a\nQ\nend\n");
        assertEquals(1, run("assets"));
        assertTrue(out().contains("FAIL assets/bad.sprite"), out());
        assertTrue(Files.exists(project.resolve("preview/apple/idle.png")));
    }

    @Test
    void variantsAreRenderedIntoTheirOwnFolders() throws Exception {
        write("assets/two.sprite",
                "size 2 1\npalette basic\nframe a\nKw\nend\nvariant two_red recolor K>r\n");
        assertEquals(0, run("assets/two.sprite"));
        assertEquals(0xFF000000, read("preview/two/a.png").getRGB(0, 0));
        assertEquals(0xFFC02020, read("preview/two_red/a.png").getRGB(0, 0));
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
