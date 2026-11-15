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

class FramesCommandTest {

    @TempDir Path project;

    private final ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errBuf = new ByteArrayOutputStream();

    @BeforeEach
    void setUp() throws Exception {
        Files.createDirectories(project.resolve("palettes"));
        Files.copy(Path.of("src/test/resources/palettes/basic.pal"), project.resolve("palettes/basic.pal"));
        Files.createDirectories(project.resolve("assets"));
        Files.copy(Path.of("src/test/resources/sprites/blink.sprite"), project.resolve("assets/blink.sprite"));
        Files.writeString(project.resolve("assets/blink.sprite"), "variant blink_red recolor K>r\n",
                StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);
    }

    private int run(String... args) throws Exception {
        return new FramesCommand(project).run(List.of(args),
                new PrintStream(outBuf, true, StandardCharsets.UTF_8),
                new PrintStream(errBuf, true, StandardCharsets.UTF_8));
    }

    private String out() { return outBuf.toString(StandardCharsets.UTF_8); }

    @Test
    void writesTheGoldenTresAndSheetPerSpriteAndVariant() throws Exception {
        assertEquals(0, run("assets"), out() + errBuf);
        assertTrue(out().contains("wrote preview/godot/blink.tres and blink.png (3 animations, 4 frames,"
                + " sheet at res://sprites/blink.png)"), out());
        assertTrue(out().contains("centered off and offset (-1, 0) puts the anchor on the node"), out());
        String golden = Files.readString(Path.of("src/test/resources/golden/blink.tres")).replace("\r\n", "\n");
        assertEquals(golden, Files.readString(project.resolve("preview/godot/blink.tres")));

        String red = Files.readString(project.resolve("preview/godot/blink_red.tres"));
        assertEquals(golden.replace("res://sprites/blink.png", "res://sprites/blink_red.png"), red);
        BufferedImage sheet = ImageIO.read(project.resolve("preview/godot/blink_red.png").toFile());
        assertEquals(7, sheet.getWidth());
        assertEquals(0xFFC02020, sheet.getRGB(1, 1), "K recoloured to r");
    }

    @Test
    void resAndOutOptions() throws Exception {
        assertEquals(0, run("assets/blink.sprite", "--res", "res://art/fx/", "--out", "godot"));
        assertTrue(Files.readString(project.resolve("godot/blink.tres"))
                .contains("path=\"res://art/fx/blink.png\""));
        assertEquals(0, run("assets/blink.sprite", "--res", "res://", "--out", "godot"));
        assertTrue(Files.readString(project.resolve("godot/blink.tres")).contains("path=\"res://blink.png\""));
    }

    @Test
    void usageErrors() throws Exception {
        assertEquals(2, run());
        assertEquals(2, run("assets", "--res", "sprites"));
        assertEquals(2, run("assets", "--res", "res://a\\b"));
        assertEquals(2, run("assets", "--padding", "1", "--extrude", "1"));
        assertEquals(1, run("missing.sprite"));
    }
}
