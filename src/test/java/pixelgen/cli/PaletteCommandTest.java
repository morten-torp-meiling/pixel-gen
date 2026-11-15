package pixelgen.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pixelgen.model.Palette;
import pixelgen.parse.Palettes;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PaletteCommandTest {

    @TempDir Path project;

    private final ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errBuf = new ByteArrayOutputStream();

    private int run(String... args) throws Exception {
        return new PaletteCommand(project).run(List.of(args),
                new PrintStream(outBuf, true, StandardCharsets.UTF_8),
                new PrintStream(errBuf, true, StandardCharsets.UTF_8));
    }

    private String out() { return outBuf.toString(StandardCharsets.UTF_8); }
    private String err() { return errBuf.toString(StandardCharsets.UTF_8); }

    private void copySample() throws Exception {
        Files.createDirectories(project.resolve("palettes"));
        Files.copy(Path.of("src/test/resources/palettes/basic.pal"), project.resolve("palettes/basic.pal"));
    }

    @Test
    void showsPaletteByNameAndWritesSwatchToPreview() throws Exception {
        copySample();
        assertEquals(0, run("basic"));
        assertTrue(out().contains("palette basic: 7 colours in 3 ramps"), out());
        assertTrue(out().contains("accents      (unordered)"), out());
        assertTrue(Files.isRegularFile(project.resolve("preview/palette-basic.png")));
        assertEquals("", err());
    }

    @Test
    void outFlagChoosesTheFolder() throws Exception {
        copySample();
        assertEquals(0, run("palettes/basic.pal", "--out", "elsewhere"));
        assertTrue(Files.isRegularFile(project.resolve("elsewhere/palette-basic.png")));
    }

    @Test
    void printsWarningsButStillSucceeds() throws Exception {
        Files.writeString(project.resolve("dark.pal"), "ramp a\n w #ffffff\n K #000000\n");
        assertEquals(0, run("dark.pal"));
        assertTrue(err().contains("warning: ") && err().contains("not lighter"), err());
    }

    @Test
    void importWritesAPalFileAndRefusesToOverwrite() throws Exception {
        Files.writeString(project.resolve("p.hex"), "1a1c2c\n5d275d\n");
        assertEquals(0, run("import", "p.hex", "lospec"));
        Palette p = Palettes.load("lospec", project);
        assertEquals(2, p.size());

        assertEquals(1, run("import", "p.hex", "lospec"));
        assertTrue(err().contains("--force"), err());
        assertEquals(0, run("import", "p.hex", "lospec", "--force"));
    }

    @Test
    void wrongArgumentsAreUsageErrors() throws Exception {
        assertEquals(2, run());
        assertEquals(2, run("import", "only-one"));
        assertEquals(2, run("basic", "--out"));
    }
}
