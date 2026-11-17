package pixelgen.cli;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

class ValidateCommandTest extends CommandTest {

    @BeforeEach
    void setUp() throws Exception {
        copy("palettes/basic.pal", "palettes/basic.pal");
        Files.createDirectories(project.resolve("assets/sub"));
        copy("sprites/apple.sprite", "assets/apple.sprite");
    }

    @Override
    pixelgen.Cli.Command command() { return new ValidateCommand(project); }

    @Test
    void checksEverythingUnderAssetsByDefault() throws Exception {
        assertEquals(0, run());
        assertTrue(out().contains("ok   assets/apple.sprite (16x16, 1 frames, 1 parts, palette basic)"), out());
        assertTrue(out().contains("1 file(s) checked, 0 with errors"), out());
    }

    @Test
    void reportsErrorsWithProjectRelativePathsAndFails() throws Exception {
        write("assets/sub/bad.sprite", "size 2 1\npalette basic\nframe a\nKQ\nend\n");
        assertEquals(1, run("assets"));
        assertTrue(out().contains("FAIL assets/sub/bad.sprite"), out());
        assertTrue(out().contains("     assets/sub/bad.sprite:4: frame 'a' pixel (1,0): 'Q' is not in palette 'basic'"), out());
        assertTrue(out().contains("2 file(s) checked, 1 with errors"), out());
    }

    @Test
    void acceptsSingleFiles() throws Exception {
        assertEquals(0, run("assets/apple.sprite"));
        assertTrue(out().contains("1 file(s) checked"), out());
    }

    @Test
    void printsWarningsWithoutFailing() throws Exception {
        write("assets/sub/empty.sprite", "size 1 1\npalette basic\nframe a\n.\nend\n");
        assertEquals(0, run("assets/sub"));
        assertTrue(out().contains("warning: assets/sub/empty.sprite:3: frame 'a' has no visible pixels"), out());
    }

    @Test
    void missingPathOrNoSpritesFails() throws Exception {
        assertEquals(1, run("nowhere"));
        assertTrue(err().contains("not found: nowhere"), err());
        assertEquals(1, run("palettes"));
        assertTrue(err().contains("no .sprite or .tileset files found"), err());
    }
}
