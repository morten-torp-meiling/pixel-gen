package pixelgen.cli;

import org.junit.jupiter.api.Test;
import pixelgen.model.Palette;
import pixelgen.parse.Palettes;

import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

class PaletteCommandTest extends CommandTest {

    @Override
    pixelgen.Cli.Command command() { return new PaletteCommand(project); }

    private void copySample() throws Exception {
        copy("palettes/basic.pal", "palettes/basic.pal");
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
        write("dark.pal", "ramp a\n w #ffffff\n K #000000\n");
        assertEquals(0, run("dark.pal"));
        assertTrue(err().contains("warning: ") && err().contains("not lighter"), err());
    }

    @Test
    void importWritesAPalFileAndRefusesToOverwrite() throws Exception {
        write("p.hex", "1a1c2c\n5d275d\n");
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
