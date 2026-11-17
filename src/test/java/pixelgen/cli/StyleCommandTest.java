package pixelgen.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StyleCommandTest {

    private static final Path RESOURCES = Path.of("src/test/resources");

    @TempDir Path project;

    private final ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errBuf = new ByteArrayOutputStream();

    private int run(String... args) throws Exception {
        return new StyleCommand(project).run(List.of(args),
                new PrintStream(outBuf, true, StandardCharsets.UTF_8),
                new PrintStream(errBuf, true, StandardCharsets.UTF_8));
    }

    private List<String> out() { return outBuf.toString(StandardCharsets.UTF_8).lines().toList(); }
    private List<String> err() { return errBuf.toString(StandardCharsets.UTF_8).lines().toList(); }

    private void copySamples() throws Exception {
        Files.createDirectories(project.resolve("palettes"));
        Files.createDirectories(project.resolve("styles"));
        Files.copy(RESOURCES.resolve("palettes/basic.pal"), project.resolve("palettes/basic.pal"));
        Files.copy(RESOURCES.resolve("styles/basic.style"), project.resolve("styles/basic.style"));
        Files.copy(RESOURCES.resolve("styles/basic.md"), project.resolve("styles/basic.md"));
    }

    @Test
    void showsAStylesSettings() throws Exception {
        copySamples();
        Files.writeString(project.resolve("styles/styles.conf"), "default = basic\n");
        assertEquals(0, run("basic"));
        assertEquals(List.of(
                "style basic (the project default)",
                "  palette  basic, 7 colours",
                "  light    top-right",
                "  outline  K selective",
                "  shadow   k",
                "  tile     8x8",
                "  guide    styles/basic.md"), out());
        assertEquals(List.of(), err());
    }

    @Test
    void printsWarningsButSucceeds() throws Exception {
        copySamples();
        Files.writeString(project.resolve("styles/plain.style"), "palette basic\n");
        assertEquals(0, run("plain"));
        assertTrue(out().contains("  outline  none"), out().toString());
        assertTrue(out().contains("  shadow   none"), out().toString());
        assertEquals(1, err().size());
        assertTrue(err().getFirst().startsWith("warning: styles/plain.style: no guide plain.md"), err().toString());
    }

    @Test
    void listsStylesAndTheDefault() throws Exception {
        copySamples();
        Files.writeString(project.resolve("styles/styles.conf"), "default = basic\n");
        Files.writeString(project.resolve("styles/plain.style"), "palette basic\n");
        Files.writeString(project.resolve("styles/plain.md"), "# plain\n");
        assertEquals(0, run());
        assertEquals(List.of(
                "default style: basic (styles/styles.conf)",
                "  basic        palette basic, light top-right, outline K selective, shadow k, tile 8x8  (default)",
                "  plain        palette basic, light top-left, outline none, shadow none, tile 16x16"), out());
    }

    @Test
    void listWithoutStyles() throws Exception {
        assertEquals(0, run());
        assertTrue(out().getFirst().startsWith("default style: built-in (no styles/styles.conf)"), out().toString());
        assertEquals("no styles in styles/", out().get(1));
    }

    @Test
    void listFailsOnABrokenStyleOrAMissingDefault() throws Exception {
        copySamples();
        Files.writeString(project.resolve("styles/styles.conf"), "default = gone\n");
        Files.writeString(project.resolve("styles/broken.style"), "palette basic\nlight up\n");
        assertEquals(1, run());
        assertTrue(err().contains("styles/broken.style:2: expected 'light <direction>', with top-left, top-right, "
                + "bottom-left or bottom-right"), err().toString());
        assertTrue(err().contains("styles/styles.conf: the default style 'gone' has no file styles/gone.style"),
                err().toString());
    }

    @Test
    void errorsAndUsage() throws Exception {
        copySamples();
        assertEquals(1, run("nope"));
        assertTrue(err().getFirst().startsWith("style 'nope' not found"), err().toString());
        Files.writeString(project.resolve("styles/broken.style"), "light up\n");
        assertEquals(1, run("broken"));
        assertEquals(2, run("a", "b"));
        assertEquals(2, run("--all"));
    }
}
