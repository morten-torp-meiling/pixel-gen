package pixelgen.parse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pixelgen.model.Light;
import pixelgen.model.Style;

import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StylesTest {

    private static final Path RESOURCES = Path.of("src/test/resources");

    @TempDir Path project;

    /** The test palette and style, copied into the temp project. */
    private void copySamples() throws Exception {
        Files.createDirectories(project.resolve("palettes"));
        Files.createDirectories(project.resolve("styles"));
        Files.copy(RESOURCES.resolve("palettes/basic.pal"), project.resolve("palettes/basic.pal"));
        Files.copy(RESOURCES.resolve("styles/basic.style"), project.resolve("styles/basic.style"));
        Files.copy(RESOURCES.resolve("styles/basic.md"), project.resolve("styles/basic.md"));
    }

    @Test
    void resolvesNamesAndPaths() {
        assertEquals(project.resolve("styles/cave.style"), Styles.resolve("cave", project));
        assertEquals(project.resolve("other/cave.style"), Styles.resolve("other/cave.style", project));
        assertEquals(project.resolve("cave.style"), Styles.resolve("cave.style", project));
    }

    @Test
    void loadsByNameWithAProjectRelativeSource() throws Exception {
        copySamples();
        Style s = Styles.load("basic", project, PaletteResolver.cached(project));
        assertEquals(Light.TOP_RIGHT, s.light());
        Files.writeString(project.resolve("styles/broken.style"), "palette basic\nlight up\n");
        List<String> e = assertThrows(ParseException.class,
                () -> Styles.load("broken", project, PaletteResolver.cached(project))).errors();
        assertTrue(e.getFirst().startsWith("styles/broken.style:2: "), e.getFirst());
        assertThrows(NoSuchFileException.class, () -> Styles.load("nope", project, PaletteResolver.cached(project)));
    }

    @Test
    void defaultNameComesFromStylesConf() throws Exception {
        assertNull(Styles.defaultName(project));
        Files.createDirectories(project.resolve("styles"));
        Files.writeString(project.resolve("styles/styles.conf"), "# nothing set\n");
        assertNull(Styles.defaultName(project));
        Files.writeString(project.resolve("styles/styles.conf"), "# the default\ndefault = basic\n");
        assertEquals("basic", Styles.defaultName(project));
    }

    @Test
    void stylesConfErrors() throws Exception {
        Files.createDirectories(project.resolve("styles"));
        Files.writeString(project.resolve("styles/styles.conf"), "default = two words\nlight = top-left\nhello\n");
        assertEquals(List.of(
                "styles/styles.conf:1: expected 'default = <style name>', like 'default = farm'",
                "styles/styles.conf:2: unknown setting 'light'; known: default",
                "styles/styles.conf:3: expected 'key = value'"),
                assertThrows(ParseException.class, () -> Styles.defaultName(project)).errors());
    }

    @Test
    void listsStyleFilesByName() throws Exception {
        assertEquals(List.of(), Styles.list(project));
        copySamples();
        Files.writeString(project.resolve("styles/aaa.style"), "palette basic\n");
        assertEquals(List.of(project.resolve("styles/aaa.style"), project.resolve("styles/basic.style")),
                Styles.list(project));
    }

    @Test
    void cachedResolverGivesTheBuiltInStyleWithoutAConf() throws Exception {
        assertSame(Style.BUILT_IN, PaletteResolver.cached(project).defaultStyle());
        PaletteResolver lambda = name -> null;
        assertSame(Style.BUILT_IN, lambda.defaultStyle());
        assertThrows(NoSuchFileException.class, () -> lambda.style("basic"));
    }

    @Test
    void cachedResolverLoadsTheDefaultStyleOnce() throws Exception {
        copySamples();
        Files.writeString(project.resolve("styles/styles.conf"), "default = basic\n");
        PaletteResolver r = PaletteResolver.cached(project);
        Style s = r.defaultStyle();
        assertEquals("basic", s.name());
        assertSame(s, r.defaultStyle());
        assertSame(s, r.style("basic"));
        assertSame(s.palette(), r.load("basic"));
    }

    @Test
    void aDefaultStyleWithoutAFileIsAnError() throws Exception {
        Files.createDirectories(project.resolve("styles"));
        Files.writeString(project.resolve("styles/styles.conf"), "default = gone\n");
        assertThrows(NoSuchFileException.class, () -> PaletteResolver.cached(project).defaultStyle());
    }
}
