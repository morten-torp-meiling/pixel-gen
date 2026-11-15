package pixelgen.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A named set of colours, each drawn with a single character in sprite grids. Colours are
 * grouped into ramps, normally ordered from dark to light.
 */
public final class Palette {

    /** The character that means "no pixel" in every grid. It can never be a palette colour. */
    public static final char TRANSPARENT = '.';

    /** One colour: the grid character, its 0xRRGGBB value, a descriptive name and its ramp. */
    public record Entry(char symbol, int rgb, String name, String ramp) {}

    /**
     * A group of related colours. {@code ordered} ramps are expected to go from dark to light,
     * and the parser warns when they don't.
     */
    public record Ramp(String name, boolean ordered, List<Entry> entries) {}

    private final String name;
    private final List<Ramp> ramps;
    private final List<String> warnings;
    private final Map<Character, Entry> bySymbol = new LinkedHashMap<>();

    public Palette(String name, List<Ramp> ramps, List<String> warnings) {
        this.name = name;
        this.ramps = List.copyOf(ramps);
        this.warnings = List.copyOf(warnings);
        for (Ramp r : this.ramps) {
            for (Entry e : r.entries()) bySymbol.put(e.symbol(), e);
        }
    }

    public String name() { return name; }

    public List<Ramp> ramps() { return ramps; }

    /** Non-fatal problems found while parsing, such as a ramp that isn't dark-to-light. */
    public List<String> warnings() { return warnings; }

    /** All colours in file order. */
    public List<Entry> entries() { return List.copyOf(bySymbol.values()); }

    public int size() { return bySymbol.size(); }

    public boolean contains(char symbol) { return bySymbol.containsKey(symbol); }

    /** The colour for a symbol, or null if the palette doesn't have it. */
    public Entry get(char symbol) { return bySymbol.get(symbol); }

    public Map<Character, Entry> bySymbol() { return Collections.unmodifiableMap(bySymbol); }
}
