package pixelgen.cli;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** tilecheck, tiles, and tilesets in the general commands (validate, review). */
class TileCommandsTest extends CommandTest {

    @BeforeEach
    void setUp() throws Exception {
        copy("palettes/basic.pal", "palettes/basic.pal");
        copy("tilesets/ground.tileset", "assets/tiles/ground.tileset");
        write("assets/tiles/plain.tileset",
                "palette basic\nsize 2 2\ntile g\ngG\nGg\nend\nvariant plain_snow recolor g>w G>k\n");
    }

    @Test
    void tilecheckReportsSeamsAndDrawsTheRepeats() throws Exception {
        TilecheckCommand cmd = new TilecheckCommand(project);
        assertEquals(1, run(cmd, "assets"));
        List<String> lines = out().lines().toList();
        assertEquals("FAIL assets/tiles/ground.tileset: 1 of 7 seams don't match", lines.get(0));
        assertTrue(lines.get(1).startsWith("     seam: the right edge doesn't continue into the left edge at rows 0-3"),
                lines.get(1));
        assertEquals("     wrote preview/ground/tilecheck.png", lines.get(2));
        assertEquals("ok   assets/tiles/plain.tileset: 1 tiles, 2 seams checked", lines.get(3));
        BufferedImage img = ImageIO.read(project.resolve("preview/ground/tilecheck.png").toFile());
        assertTrue(img.getWidth() > 4 * 4 * 4 * 3, "four tiles side by side, grass repeated 4x at scale 4");

        assertEquals(0, run(cmd, "assets/tiles/plain.tileset", "--scale", "2", "--out", "checks"));
        assertTrue(Files.exists(project.resolve("checks/plain/tilecheck.png")));
    }

    @Test
    void tilesWritesTheGoldenTileSetAndAnAtlasPerVariant() throws Exception {
        assertEquals(0, run(new TilesCommand(project), "assets"), out() + err());
        assertTrue(out().contains("wrote preview/godot/ground.tres and ground.png (4 tiles of 4x4 in 2x3,"
                + " atlas at res://tiles/ground.png)"), out());
        String golden = Files.readString(RESOURCES.resolve("golden/ground.tres")).replace("\r\n", "\n");
        assertEquals(golden, Files.readString(project.resolve("preview/godot/ground.tres")));
        BufferedImage atlas = ImageIO.read(project.resolve("preview/godot/ground.png").toFile());
        assertEquals(12, atlas.getWidth());
        assertEquals(8, atlas.getHeight());
        BufferedImage snow = ImageIO.read(project.resolve("preview/godot/plain_snow.png").toFile());
        assertEquals(0xFFFFFFFF, snow.getRGB(0, 0));
        assertTrue(Files.readString(project.resolve("preview/godot/plain_snow.tres"))
                .contains("path=\"res://tiles/plain_snow.png\""));
    }

    @Test
    void tilesUsageErrors() throws Exception {
        TilesCommand cmd = new TilesCommand(project);
        assertEquals(2, run(cmd));
        assertEquals(2, run(cmd, "assets", "--res", "tiles"));
        assertEquals(2, run(cmd, "assets", "--extrude", "1"));
        assertEquals(1, run(cmd, "missing.tileset"));
        Files.createDirectories(project.resolve("empty"));
        assertEquals(1, run(cmd, "empty"));
        assertTrue(err().contains("no .tileset files found in empty"));
    }

    @Test
    void validateAndReviewTakeTilesets() throws Exception {
        assertEquals(0, run(new ValidateCommand(project), "assets"), out());
        assertTrue(out().contains("ok   assets/tiles/ground.tileset (4 tiles of 4x4, 0 parts, 3 columns, palette basic,"
                + " wrap x: edge)"), out());
        assertTrue(out().contains("variants plain_snow"), out());
        assertEquals(0, run(new ReviewCommand(project), "assets/tiles/ground.tileset"), out() + err());
        assertTrue(Files.exists(project.resolve("preview/ground/review.png")));
    }
}
