package pixelgen.cli;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

class FramesCommandTest extends CommandTest {

    @BeforeEach
    void setUp() throws Exception {
        copy("palettes/basic.pal", "palettes/basic.pal");
        copy("sprites/blink.sprite", "assets/blink.sprite");
        append(project.resolve("assets/blink.sprite"), "variant blink_red recolor K>r\n");
    }

    @Override
    pixelgen.Cli.Command command() { return new FramesCommand(project); }

    @Test
    void writesTheGoldenTresAndSheetPerSpriteAndVariant() throws Exception {
        assertEquals(0, run("assets"), out() + err());
        assertTrue(out().contains("wrote preview/godot/blink.tres and blink.png (3 animations, 4 frames,"
                + " sheet at res://sprites/blink.png)"), out());
        assertTrue(out().contains("centered off and offset (-1, 0) puts the anchor on the node"), out());
        String golden = Files.readString(RESOURCES.resolve("golden/blink.tres")).replace("\r\n", "\n");
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
