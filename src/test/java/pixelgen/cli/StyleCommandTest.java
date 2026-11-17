package pixelgen.cli;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StyleCommandTest extends CommandTest {

    @Override
    pixelgen.Cli.Command command() { return new StyleCommand(project); }

    private void copySamples() throws Exception {
        copy("palettes/basic.pal", "palettes/basic.pal");
        copy("styles/basic.style", "styles/basic.style");
        copy("styles/basic.md", "styles/basic.md");
    }

    @Test
    void showsAStylesSettings() throws Exception {
        copySamples();
        write("styles/styles.conf", "default = basic\n");
        assertEquals(0, run("basic"));
        assertEquals(List.of(
                "style basic (the project default)",
                "  palette  basic, 7 colours",
                "  light    top-right",
                "  outline  K selective",
                "  shadow   k",
                "  tile     8x8",
                "  guide    styles/basic.md"), lines(out()));
        assertEquals(List.of(), lines(err()));
    }

    @Test
    void printsWarningsButSucceeds() throws Exception {
        copySamples();
        write("styles/plain.style", "palette basic\n");
        assertEquals(0, run("plain"));
        assertTrue(lines(out()).contains("  outline  none"), out());
        assertTrue(lines(out()).contains("  shadow   none"), out());
        assertEquals(1, lines(err()).size());
        assertTrue(lines(err()).getFirst().startsWith("warning: styles/plain.style: no guide plain.md"), err());
    }

    @Test
    void listsStylesAndTheDefault() throws Exception {
        copySamples();
        write("styles/styles.conf", "default = basic\n");
        write("styles/plain.style", "palette basic\n");
        write("styles/plain.md", "# plain\n");
        assertEquals(0, run());
        assertEquals(List.of(
                "default style: basic (styles/styles.conf)",
                "  basic        palette basic, light top-right, outline K selective, shadow k, tile 8x8  (default)",
                "  plain        palette basic, light top-left, outline none, shadow none, tile 16x16"), lines(out()));
    }

    @Test
    void listWithoutStyles() throws Exception {
        assertEquals(0, run());
        assertTrue(lines(out()).getFirst().startsWith("default style: built-in (no styles/styles.conf)"), out());
        assertEquals("no styles in styles/", lines(out()).get(1));
    }

    @Test
    void listFailsOnABrokenStyleOrAMissingDefault() throws Exception {
        copySamples();
        write("styles/styles.conf", "default = gone\n");
        write("styles/broken.style", "palette basic\nlight up\n");
        assertEquals(1, run());
        assertTrue(err().contains("styles/broken.style:2: expected 'light <direction>', with top-left, top-right, "
                + "bottom-left or bottom-right"), err());
        assertTrue(err().contains("styles/styles.conf: the default style 'gone' has no file styles/gone.style"),
                err());
    }

    @Test
    void errorsAndUsage() throws Exception {
        copySamples();
        assertEquals(1, run("nope"));
        assertTrue(lines(err()).getFirst().startsWith("style 'nope' not found"), err());
        write("styles/broken.style", "light up\n");
        assertEquals(1, run("broken"));
        assertEquals(2, run("a", "b"));
        assertEquals(2, run("--all"));
    }
}
