package pixelgen.godot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pixelgen.model.Sprite;
import pixelgen.parse.Palettes;
import pixelgen.parse.ParseException;
import pixelgen.parse.SpriteParser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The pieces of the export: settings, uid keeping, scenes. */
class ExportPartsTest {

    @TempDir Path dir;

    private Path godotProject() throws Exception {
        Path game = dir.resolve("game");
        Files.createDirectories(game);
        Files.writeString(game.resolve("project.godot"), "config_version=5\n");
        return game;
    }

    @Test
    void configWithDefaultsAndRelativeProject() throws Exception {
        Path game = godotProject();
        Files.createDirectories(dir.resolve("art-src"));
        ExportConfig c = ExportConfig.parse("# comment\ngodot.project = ../game\n", "pixelgen.conf", dir.resolve("art-src"));
        assertEquals(game.toAbsolutePath().normalize(), c.project());
        assertEquals("res://art", c.art());
        assertTrue(c.scenes());
        assertEquals(game.resolve("art/chars").toAbsolutePath().normalize(), c.toDisk("res://art/chars").normalize());

        ExportConfig d = ExportConfig.parse("godot.project = game\ngodot.art = res://gfx/\nscenes = false\n", "c", dir);
        assertEquals("res://gfx", d.art());
        assertFalse(d.scenes());
        assertEquals("res://", ExportConfig.parse("godot.project = game\ngodot.art = res://\n", "c", dir).art());
    }

    @Test
    void configErrors() throws Exception {
        godotProject();
        List<String> e = assertThrows(ParseException.class, () -> ExportConfig.parse(
                "godot.project = nowhere\ngodot.art = art\nscenes = yes\ncolour = blue\nhello\nscenes = true\n",
                "c", dir)).errors();
        assertEquals(List.of(
                "c:2: godot.art must be a folder like res://art, not 'art'",
                "c:3: scenes must be true or false, not 'yes'",
                "c:4: unknown setting 'colour'; known: godot.project, godot.art, scenes",
                "c:5: expected 'key = value'",
                "c:6: 'scenes' is already set on line 3"), e.subList(0, 5));
        assertTrue(e.get(5).startsWith("c:1: no project.godot in "), e.get(5));
        assertEquals(List.of("c: missing 'godot.project = <folder with project.godot>'"),
                assertThrows(ParseException.class, () -> ExportConfig.parse("", "c", dir)).errors());
    }

    @Test
    void uidsAreCarriedOver() {
        String fresh = "[gd_resource type=\"SpriteFrames\" load_steps=2 format=3]\n\n[resource]\n";
        String saved = "[gd_resource type=\"SpriteFrames\" load_steps=2 format=3 uid=\"uid://b4x7k2\"]\n\n[resource]\n";
        assertEquals("uid://b4x7k2", Uids.find(saved));
        assertNull(Uids.find(fresh));
        assertEquals(saved, Uids.keep(saved, fresh));
        assertEquals(fresh, Uids.keep(null, fresh));
        assertEquals(fresh, Uids.keep(fresh, fresh));
        // An ext_resource uid further down is not the file's own.
        assertNull(Uids.find("[gd_scene format=3]\n[ext_resource uid=\"uid://abc\" path=\"x\"]\n"));
    }

    @Test
    void sceneMatchesTheHandWrittenGoldenFile() throws Exception {
        Sprite s = SpriteParser.parseFile(Path.of("src/test/resources/sprites/blink.sprite"), "blink.sprite",
                n -> Palettes.load("basic", Path.of("src/test/resources")));
        assertEquals(Files.readString(Path.of("src/test/resources/golden/blink.tscn")).replace("\r\n", "\n"),
                SceneWriter.write(s, "blink", "res://art/blink.tres"));
        assertFalse(SceneWriter.write(s, null, "res://x.tres").contains("autoplay"));
    }

    @Test
    void joinResPaths() {
        assertEquals("res://art/x.png", Exporter.join("res://art", "x.png"));
        assertEquals("res://x.png", Exporter.join("res://", "x.png"));
    }
}
