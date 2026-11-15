package pixelgen.godot;

import org.junit.jupiter.api.Test;
import pixelgen.model.Tileset;
import pixelgen.parse.Palettes;
import pixelgen.parse.TilesetParser;
import pixelgen.render.SheetPacker;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TileSetWriterTest {

    private static Tileset ground() throws Exception {
        return TilesetParser.parseFile(Path.of("src/test/resources/tilesets/ground.tileset"), "ground.tileset",
                n -> Palettes.load("basic", Path.of("src/test/resources")));
    }

    @Test
    void matchesTheHandWrittenGoldenFile() throws Exception {
        Tileset t = ground();
        String text = TileSetWriter.write(t, SheetPacker.grid(t.sprite(), t.columns(), 0, 0), "res://tiles/ground.png");
        assertEquals(Files.readString(Path.of("src/test/resources/golden/ground.tres")).replace("\r\n", "\n"), text);
    }

    @Test
    void paddingBecomesMarginsAndSeparationWithTheSameCoordinates() throws Exception {
        Tileset t = ground();
        String text = TileSetWriter.write(t, SheetPacker.grid(t.sprite(), t.columns(), 2, 1), "res://x.png");
        assertTrue(text.contains("texture = ExtResource(\"1_atlas\")\nmargins = Vector2i(2, 2)\n"
                + "separation = Vector2i(2, 2)\ntexture_region_size = Vector2i(4, 4)\n"), text);
        assertTrue(text.contains("2:0/0/custom_data_0 = \"edge\"\n0:1/0 = 0\n"), text);
    }
}
