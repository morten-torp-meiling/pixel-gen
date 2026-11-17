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

    /**
     * One colour: the grid character, its 0xRRGGBB value, its opacity (1-255; 255 is opaque),
     * a descriptive name and its ramp.
     */
    public record Entry(char symbol, int rgb, int alpha, String name, String ramp) {
        /** Packed 0xAARRGGBB, as used by images. */
        public int argb() { return (alpha << 24) | (rgb & 0xFFFFFF); }

        /** True for see-through colours such as a soft shadow. */
        public boolean translucent() { return alpha < 255; }

        /** {@code #rrggbb}, or {@code #rrggbbaa} for translucent colours. */
        public String hex() { return Colors.toHex(rgb, alpha); }
    }

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

    /** The ramp with this name, or null. */
    public Ramp ramp(String rampName) {
        for (Ramp r : ramps) if (r.name().equals(rampName)) return r;
        return null;
    }

    /** True when the symbol is a palette colour that is fully opaque. */
    public boolean isOpaque(char symbol) {
        Entry e = bySymbol.get(symbol);
        return e != null && !e.translucent();
    }

    /** True when some colour is translucent. */
    public boolean hasTranslucent() {
        for (Entry e : bySymbol.values()) if (e.translucent()) return true;
        return false;
    }
}
