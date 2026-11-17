package pixelgen.cli;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExportCommandTest {

    @TempDir Path root;
    Path tool, game;

    private final ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errBuf = new ByteArrayOutputStream();

    @BeforeEach
    void setUp() throws Exception {
        tool = root.resolve("art");
        game = root.resolve("game");
        Files.createDirectories(game);
        Files.writeString(game.resolve("project.godot"), "config_version=5\n");
        Files.createDirectories(tool.resolve("palettes"));
        Files.copy(Path.of("src/test/resources/palettes/basic.pal"), tool.resolve("palettes/basic.pal"));
        Files.createDirectories(tool.resolve("assets/tiles"));
        Files.copy(Path.of("src/test/resources/sprites/blink.sprite"), tool.resolve("assets/blink.sprite"));
        append("assets/blink.sprite", "variant blink_red recolor K>r\n");
        Files.copy(Path.of("src/test/resources/tilesets/ground.tileset"), tool.resolve("assets/tiles/ground.tileset"));
        Files.writeString(tool.resolve("pixelgen.conf"), "godot.project = ../game\n");
    }

    private void append(String file, String text) throws Exception {
        Files.writeString(tool.resolve(file), text, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
    }

    private int run(String... args) throws Exception {
        outBuf.reset();
        errBuf.reset();
        return new ExportCommand(tool).run(List.of(args), new PrintStream(outBuf, true, StandardCharsets.UTF_8),
                new PrintStream(errBuf, true, StandardCharsets.UTF_8));
    }

    private String out() { return outBuf.toString(StandardCharsets.UTF_8); }

    private String done() {
        return out().lines().filter(l -> l.startsWith("done: ")).findFirst().orElse(out())
                .replaceAll(" \\(Godot project .*\\)$", "");
    }

    private static String golden(String name) throws Exception {
        return Files.readString(Path.of("src/test/resources/golden/" + name)).replace("\r\n", "\n");
    }

    @Test
    void firstRunExportsEverythingIntoTheArtFolderSecondRunNothing() throws Exception {
        assertEquals(0, run(), out() + errBuf);
        assertTrue(out().contains("exported assets/blink.sprite -> res://art: blink, blink_red"), out());
        assertTrue(out().contains("exported assets/tiles/ground.tileset -> res://art/tiles: ground\n"
                .replace("\n", System.lineSeparator()) + "     warning: 1 seam doesn't match; run tilecheck to see it"),
                out());
        assertEquals("done: 2 exported, 0 unchanged, 0 removed, 0 failed", done());

        for (String f : List.of("blink.png", "blink.tres", "blink.tscn", "blink_red.png", "blink_red.tres",
                "blink_red.tscn", "tiles/ground.png", "tiles/ground.tres")) {
            assertTrue(Files.isRegularFile(game.resolve("art/" + f)), f);
        }
        assertEquals(golden("blink.tres").replace("res://sprites/blink.png", "res://art/blink.png"),
                Files.readString(game.resolve("art/blink.tres")));
        assertEquals(golden("blink.tscn"), Files.readString(game.resolve("art/blink.tscn")));
        assertEquals(golden("ground.tres").replace("res://tiles/ground.png", "res://art/tiles/ground.png"),
                Files.readString(game.resolve("art/tiles/ground.tres")));

        assertEquals(0, run());
        assertEquals("done: 0 exported, 2 unchanged, 0 removed, 0 failed", done());
        assertTrue(out().lines().noneMatch(l -> l.startsWith("exported ")), out());
    }

    @Test
    void onlyChangedSourcesAndMissingFilesAreExportedAgain() throws Exception {
        run();
        append("assets/blink.sprite", "# a comment is a change too\n");
        assertEquals(0, run());
        assertTrue(out().contains("exported assets/blink.sprite"), out());
        assertEquals("done: 1 exported, 1 unchanged, 0 removed, 0 failed", done());

        Files.delete(game.resolve("art/tiles/ground.png"));
        run();
        assertTrue(out().contains("exported assets/tiles/ground.tileset"), out());
        assertEquals("done: 1 exported, 1 unchanged, 0 removed, 0 failed", done());

        // A palette change reaches every source that uses it.
        Files.writeString(tool.resolve("palettes/basic.pal"),
                Files.readString(tool.resolve("palettes/basic.pal")).replace("#c02020", "#c02121"));
        run();
        assertEquals("done: 2 exported, 0 unchanged, 0 removed, 0 failed", done());

        run("--all");
        assertEquals("done: 2 exported, 0 unchanged, 0 removed, 0 failed", done());
    }

    @Test
    void keepsGodotsUidsAndRemovesFilesNoLongerWritten() throws Exception {
        run();
        Path tres = game.resolve("art/blink.tres");
        Files.writeString(tres, Files.readString(tres).replace("format=3]", "format=3 uid=\"uid://cq1x2\"]"));
        Files.writeString(game.resolve("art/blink_red.png.import"), "[remap]\n");
        Files.writeString(game.resolve("art/mine.png"), "not the export's");

        String text = Files.readString(tool.resolve("assets/blink.sprite"));
        Files.writeString(tool.resolve("assets/blink.sprite"), text.replace("variant blink_red recolor K>r\n", ""));
        Files.delete(tool.resolve("assets/tiles/ground.tileset"));
        assertEquals(0, run(), out());

        assertTrue(Files.readString(tres).startsWith(
                "[gd_resource type=\"SpriteFrames\" load_steps=6 format=3 uid=\"uid://cq1x2\"]\n"));
        assertTrue(out().contains("removed res://art/blink_red.png"), out());
        assertTrue(out().contains("removed res://art/tiles/ground.tres"), out());
        assertEquals("done: 1 exported, 0 unchanged, 5 removed, 0 failed", done());
        assertFalse(Files.exists(game.resolve("art/blink_red.png.import")));
        assertFalse(Files.exists(game.resolve("art/tiles/ground.png")));
        assertTrue(Files.exists(game.resolve("art/mine.png")), "files the export didn't write are left alone");
    }

    @Test
    void aBrokenSourceKeepsItsFilesUntilFixed() throws Exception {
        run();
        String text = Files.readString(tool.resolve("assets/blink.sprite"));
        Files.writeString(tool.resolve("assets/blink.sprite"), text.replace("anim fade", "anim fade nonsense"));
        assertEquals(1, run());
        assertTrue(out().contains("FAIL assets/blink.sprite (its files in the Godot project are left as they were)"));
        assertEquals("done: 0 exported, 1 unchanged, 0 removed, 1 failed", done());
        assertTrue(Files.exists(game.resolve("art/blink_red.png")));

        Files.writeString(tool.resolve("assets/blink.sprite"), text);
        assertEquals(0, run());
        assertEquals("done: 0 exported, 2 unchanged, 0 removed, 0 failed", done(), "fixed back to the exported text");
    }

    @Test
    void scenesCanBeTurnedOffAndSettingsChangeTheHash() throws Exception {
        run();
        Files.writeString(tool.resolve("pixelgen.conf"), "godot.project = ../game\nscenes = false\n");
        run();
        assertEquals("done: 2 exported, 0 unchanged, 2 removed, 0 failed", done());
        assertFalse(Files.exists(game.resolve("art/blink.tscn")));
    }

    @Test
    void aStyleChangeReachesTheSourcesThatUseIt() throws Exception {
        run();
        Files.createDirectories(tool.resolve("styles"));
        Files.writeString(tool.resolve("styles/main.style"), "palette basic\n");
        Files.writeString(tool.resolve("styles/main.md"), "# main\n");
        Files.writeString(tool.resolve("styles/other.style"), "palette basic\n");
        Files.writeString(tool.resolve("styles/other.md"), "# other\n");
        run();
        assertEquals("done: 0 exported, 2 unchanged, 0 removed, 0 failed", done(), "styles no file uses");

        Files.writeString(tool.resolve("styles/styles.conf"), "default = main\n");
        run();
        assertEquals("done: 2 exported, 0 unchanged, 0 removed, 0 failed", done(), "a new default style");

        Files.writeString(tool.resolve("styles/main.style"), "palette basic\nlight top-right\n");
        run();
        assertEquals("done: 2 exported, 0 unchanged, 0 removed, 0 failed", done(), "a setting of the style");

        append("assets/blink.sprite", "style other\n");
        run();
        Files.writeString(tool.resolve("styles/main.style"), "palette basic\nshadow r\n");
        run();
        assertEquals("done: 1 exported, 1 unchanged, 0 removed, 0 failed", done(), "only the tileset uses main");
    }

    @Test
    void settingsErrors() throws Exception {
        assertEquals(2, run("--everything"));
        Files.writeString(tool.resolve("pixelgen.conf"), "godot.project = ../nowhere\n");
        assertEquals(1, run());
        assertTrue(errBuf.toString(StandardCharsets.UTF_8).contains("no project.godot in"));
        Files.delete(tool.resolve("pixelgen.conf"));
        assertEquals(1, run());
        assertTrue(errBuf.toString(StandardCharsets.UTF_8).contains("create one with a line like 'godot.project"));
    }
}
