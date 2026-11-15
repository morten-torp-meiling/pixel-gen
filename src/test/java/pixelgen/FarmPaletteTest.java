package pixelgen;

import org.junit.jupiter.api.Test;
import pixelgen.model.Palette;
import pixelgen.parse.Palettes;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Guards the project's real default palette, palettes/farm.pal. */
class FarmPaletteTest {

    @Test
    void farmPaletteLoadsWithoutWarnings() throws Exception {
        Palette p = Palettes.load("farm", Path.of(""));
        assertEquals(List.of(), p.warnings());
        assertTrue(p.size() >= 32 && p.size() <= 48, "size " + p.size());
        for (Palette.Ramp r : p.ramps()) {
            if (!r.name().equals("effects")) assertTrue(r.ordered(), r.name() + " should be ordered");
        }
    }

    @Test
    void shadowColourIsTranslucentInk() throws Exception {
        Palette.Entry shadow = Palettes.load("farm", Path.of("")).get('~');
        assertEquals("effects", shadow.ramp());
        assertTrue(shadow.alpha() > 0x30 && shadow.alpha() < 0x80, "alpha " + shadow.alpha());
    }

    @Test
    void farmPaletteHasTheRampsTheStyleGuideReliesOn() throws Exception {
        Palette p = Palettes.load("farm", Path.of(""));
        List<String> names = p.ramps().stream().map(Palette.Ramp::name).toList();
        for (String needed : List.of("ink", "skin", "hair", "wood", "soil", "leaf", "stone", "water")) {
            assertTrue(names.contains(needed), "missing ramp " + needed + " in " + names);
        }
    }
}
