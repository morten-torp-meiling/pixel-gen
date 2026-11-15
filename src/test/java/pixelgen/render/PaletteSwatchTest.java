package pixelgen.render;

import org.junit.jupiter.api.Test;
import pixelgen.model.Palette;
import pixelgen.parse.PaletteParser;

import java.awt.image.BufferedImage;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PaletteSwatchTest {

    @Test
    void drawsEveryColourInItsRampRow() throws Exception {
        Palette p = PaletteParser.parseFile(Path.of("src/test/resources/palettes/basic.pal"));
        BufferedImage img = PaletteSwatch.render(p);

        for (int r = 0; r < p.ramps().size(); r++) {
            var entries = p.ramps().get(r).entries();
            for (int i = 0; i < entries.size(); i++) {
                // Corner pixel of the square: inside the colour, away from the symbol text.
                int rgb = img.getRGB(PaletteSwatch.cellX(i) + 2, PaletteSwatch.cellY(r) + 2) & 0xFFFFFF;
                assertEquals(entries.get(i).rgb(), rgb, "ramp " + r + " colour " + i);
            }
        }
    }

    @Test
    void imageFitsTheWidestRampAndAllRows() throws Exception {
        Palette p = PaletteParser.parseFile(Path.of("src/test/resources/palettes/basic.pal"));
        BufferedImage img = PaletteSwatch.render(p);
        assertTrue(img.getWidth() >= PaletteSwatch.cellX(3) - PaletteSwatch.GAP, "width " + img.getWidth());
        assertTrue(img.getHeight() >= PaletteSwatch.cellY(2) + PaletteSwatch.CELL, "height " + img.getHeight());
        assertEquals(PaletteSwatch.BACKGROUND.getRGB(), img.getRGB(img.getWidth() - 1, img.getHeight() - 1));
    }
}
