package pixelgen.cli;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ValidateCommandTest {

    @TempDir Path project;

    private final ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errBuf = new ByteArrayOutputStream();

    @BeforeEach
    void setUp() throws Exception {
        Files.createDirectories(project.resolve("palettes"));
        Files.copy(Path.of("src/test/resources/palettes/basic.pal"), project.resolve("palettes/basic.pal"));
        Files.createDirectories(project.resolve("assets/sub"));
        Files.copy(Path.of("src/test/resources/sprites/apple.sprite"), project.resolve("assets/apple.sprite"));
    }

    private int run(String... args) throws Exception {
        return new ValidateCommand(project).run(List.of(args),
                new PrintStream(outBuf, true, StandardCharsets.UTF_8),
                new PrintStream(errBuf, true, StandardCharsets.UTF_8));
    }

    private String out() { return outBuf.toString(StandardCharsets.UTF_8); }
    private String err() { return errBuf.toString(StandardCharsets.UTF_8); }

    @Test
    void checksEverythingUnderAssetsByDefault() throws Exception {
        assertEquals(0, run());
        assertTrue(out().contains("ok   assets/apple.sprite (16x16, 1 frames, 1 parts, palette basic)"), out());
        assertTrue(out().contains("1 file(s) checked, 0 with errors"), out());
    }

    @Test
    void reportsErrorsWithProjectRelativePathsAndFails() throws Exception {
        Files.writeString(project.resolve("assets/sub/bad.sprite"), "size 2 1\npalette basic\nframe a\nKQ\nend\n");
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
        Files.writeString(project.resolve("assets/sub/empty.sprite"), "size 1 1\npalette basic\nframe a\n.\nend\n");
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
