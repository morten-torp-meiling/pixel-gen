package pixelgen.parse;

import org.junit.jupiter.api.Test;
import pixelgen.model.Palette;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HexPaletteImporterTest {

    @Test
    void producesAPaletteTheParserAccepts() throws Exception {
        String pal = HexPaletteImporter.toPal("1a1c2c\n#5d275d\n\nb13e53\n", "p.hex", "lospec");
        Palette p = PaletteParser.parse(pal, "lospec.pal", "x");
        assertEquals("lospec", p.name());
        assertEquals(3, p.size());
        assertEquals(0x1a1c2c, p.get('A').rgb());
        assertEquals(0x5d275d, p.get('B').rgb());
        assertEquals(0xb13e53, p.get('C').rgb());
        assertFalse(p.ramps().getFirst().ordered(), "imported ramp must be unordered");
        assertEquals(List.of(), p.warnings());
    }

    @Test
    void reportsBadLinesWithLineNumbers() {
        ParseException e = assertThrows(ParseException.class,
                () -> HexPaletteImporter.toPal("1a1c2c\nnope\n", "p.hex", "x"));
        assertTrue(e.errors().getFirst().startsWith("p.hex:2: 'nope'"), e.errors().toString());
    }

    @Test
    void rejectsMoreColoursThanSymbols() {
        String many = "000000\n".repeat(HexPaletteImporter.SYMBOLS.length() + 1);
        ParseException e = assertThrows(ParseException.class, () -> HexPaletteImporter.toPal(many, "p.hex", "x"));
        assertTrue(e.getMessage().contains("at most"), e.getMessage());
    }

    @Test
    void symbolListHasNoReservedOrRepeatedCharacters() {
        String s = HexPaletteImporter.SYMBOLS;
        assertFalse(s.contains(".") || s.contains("#") || s.contains(" "));
        assertEquals(s.length(), s.chars().distinct().count());
    }
}
