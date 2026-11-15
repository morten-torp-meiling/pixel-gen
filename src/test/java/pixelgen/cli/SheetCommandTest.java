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

class SheetCommandTest {

    @TempDir Path project;

    private final ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errBuf = new ByteArrayOutputStream();

    @BeforeEach
    void setUp() throws Exception {
        Files.createDirectories(project.resolve("palettes"));
        Files.copy(Path.of("src/test/resources/palettes/basic.pal"), project.resolve("palettes/basic.pal"));
        Files.createDirectories(project.resolve("assets"));
        Files.writeString(project.resolve("assets/blink.sprite"),
                "size 2 1\npalette basic\nframe on\nKK\nend\nframe off\nww\nend\n"
                        + "anim blink fps 2 loop: on off\nvariant blink_red recolor K>r\n");
    }

    private int run(String... args) throws Exception {
        return new SheetCommand(project).run(List.of(args),
                new PrintStream(outBuf, true, StandardCharsets.UTF_8),
                new PrintStream(errBuf, true, StandardCharsets.UTF_8));
    }

    private String out() { return outBuf.toString(StandardCharsets.UTF_8); }

    @Test
    void writesASheetPerSpriteAndVariantWithTheSameLayout() throws Exception {
        assertEquals(0, run("assets"));
        assertTrue(out().contains("wrote preview/blink/sheet.png (7x3: 2 frames of 2x1 in 1 rows)"), out());
        assertTrue(out().contains("wrote preview/blink_red/sheet.png (7x3"), out());
        BufferedImage plain = ImageIO.read(project.resolve("preview/blink/sheet.png").toFile());
        BufferedImage red = ImageIO.read(project.resolve("preview/blink_red/sheet.png").toFile());
        assertEquals(0xFF000000, plain.getRGB(1, 1));
        assertEquals(0xFFC02020, red.getRGB(1, 1));
        assertEquals(0xFFFFFFFF, red.getRGB(4, 1), "same place, unchanged white");
    }

    @Test
    void paddingExtrudeListAndOut() throws Exception {
        assertEquals(0, run("assets/blink.sprite", "--padding", "2", "--extrude", "1", "--list", "--out", "sheets"));
        assertTrue(out().contains("wrote sheets/blink/sheet.png (10x5"), out());
        List<String> lines = out().lines().toList();
        int row = lines.indexOf("  blink:");
        assertTrue(row >= 0, out());
        assertEquals(List.of("    on at 2,2", "    off at 6,2"), lines.subList(row + 1, row + 3));
    }

    @Test
    void usageErrors() throws Exception {
        assertEquals(2, run());
        assertEquals(2, run("assets", "--padding", "x"));
        assertEquals(2, run("assets", "--padding", "99"));
        assertEquals(2, run("assets", "--padding", "1", "--extrude", "1"));
        assertEquals(2, run("assets", "--scale", "2"));
        assertEquals(1, run("missing.sprite"));
    }
}
