package pixelgen.parse;

import pixelgen.model.Colors;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts a Lospec {@code .hex} palette (one {@code rrggbb} per line) into {@code .pal} text.
 * Symbols are assigned in a fixed order, and every colour goes in one {@code unordered} ramp,
 * because .hex files have no ramps. Group the colours into ramps by hand afterwards.
 */
public final class HexPaletteImporter {

    /** Symbols handed out in this order: letters, digits, then punctuation. */
    public static final String SYMBOLS =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!$%&*+-/:;<=>?@^_~";

    private HexPaletteImporter() {}

    public static String toPal(String hexText, String source, String paletteName) throws ParseException {
        List<String> errors = new ArrayList<>();
        List<Integer> colours = new ArrayList<>();
        String[] lines = hexText.split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].strip();
            if (line.isEmpty()) continue;
            try {
                colours.add(Colors.parseHex(line));
            } catch (IllegalArgumentException e) {
                errors.add(source + ":" + (i + 1) + ": " + e.getMessage());
            }
        }
        if (errors.isEmpty() && colours.isEmpty()) errors.add(source + ": no colours found");
        if (colours.size() > SYMBOLS.length()) {
            errors.add(source + ": " + colours.size() + " colours, but at most " + SYMBOLS.length()
                    + " symbols are available");
        }
        if (!errors.isEmpty()) throw new ParseException(errors);

        StringBuilder sb = new StringBuilder();
        sb.append("# Imported from ").append(source).append('\n');
        sb.append("# Group the colours into ramps (dark to light) and give them names.\n");
        sb.append("palette ").append(paletteName).append("\n\n");
        sb.append("ramp imported unordered\n");
        for (int i = 0; i < colours.size(); i++) {
            sb.append("  ").append(SYMBOLS.charAt(i)).append("  ").append(Colors.toHex(colours.get(i)))
                    .append(String.format("  colour %02d", i + 1)).append('\n');
        }
        return sb.toString();
    }
}
