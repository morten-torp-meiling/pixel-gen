package pixelgen.parse;

import pixelgen.model.Colors;
import pixelgen.model.Palette;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parses {@code .pal} files.
 *
 * <pre>
 * # comment (a line whose first non-space character is #)
 * palette farm               optional; defaults to the file name
 * ramp skin                  starts a ramp; colours below it belong to it
 *   J  #6b3a32  skin shadow  symbol, colour, optional name
 * ramp accents unordered     'unordered' turns off the dark-to-light check
 * </pre>
 *
 * Symbols are single printable ASCII characters other than {@code .} (transparent) and {@code #}.
 */
public final class PaletteParser {

    /** Lightness steps smaller than this (OKLab L) are flagged as hard to tell apart. */
    public static final double MIN_LIGHTNESS_STEP = 0.04;

    private PaletteParser() {}

    public static Palette parseFile(Path file) throws IOException, ParseException {
        String fileName = file.getFileName().toString();
        String defaultName = fileName.endsWith(".pal") ? fileName.substring(0, fileName.length() - 4) : fileName;
        return parse(Files.readString(file, StandardCharsets.UTF_8), file.toString(), defaultName);
    }

    public static Palette parse(String text, String source, String defaultName) throws ParseException {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        String name = defaultName;

        List<Palette.Ramp> ramps = new ArrayList<>();
        Map<String, Integer> rampLines = new HashMap<>();
        Map<Character, Integer> symbolLines = new HashMap<>();
        Map<Integer, String> colourOwners = new HashMap<>();

        String rampName = null;
        boolean rampOrdered = true;
        boolean rampHadColourLines = false;  // even invalid ones: avoids a follow-on "no colours" error
        int rampLine = 0;
        List<Palette.Entry> rampEntries = new ArrayList<>();

        String[] lines = text.split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            int lineNo = i + 1;
            String line = lines[i].strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] tokens = line.split("\\s+");
            String where = source + ":" + lineNo + ": ";

            switch (tokens[0]) {
                case "palette" -> {
                    if (tokens.length != 2) errors.add(where + "expected 'palette <name>'");
                    else name = tokens[1];
                }
                case "ramp" -> {
                    if (rampName != null) {
                        ramps.add(new Palette.Ramp(rampName, rampOrdered, List.copyOf(rampEntries)));
                        checkRamp(ramps.getLast(), rampHadColourLines, source, rampLine, errors, warnings);
                    }
                    rampEntries.clear();
                    rampHadColourLines = false;
                    rampLine = lineNo;
                    boolean flagOk = tokens.length == 2 || (tokens.length == 3 && tokens[2].equals("unordered"));
                    if (!flagOk) {
                        errors.add(where + "expected 'ramp <name>' or 'ramp <name> unordered'");
                        rampName = "?";
                        rampOrdered = false;
                    } else {
                        rampName = tokens[1];
                        rampOrdered = tokens.length == 2;
                        Integer first = rampLines.putIfAbsent(rampName, lineNo);
                        if (first != null) {
                            errors.add(where + "ramp '" + rampName + "' is already defined on line " + first);
                        }
                    }
                }
                default -> {
                    rampHadColourLines = true;
                    if (tokens[0].length() != 1) {
                        errors.add(where + "unknown line '" + line
                                + "'; expected 'palette', 'ramp' or '<symbol> #rrggbb [name]'");
                        continue;
                    }
                    char symbol = tokens[0].charAt(0);
                    if (symbol == Palette.TRANSPARENT || symbol == '#' || symbol < 33 || symbol > 126) {
                        errors.add(where + "'" + symbol + "' cannot be a colour symbol"
                                + (symbol == Palette.TRANSPARENT ? " ('.' means transparent)" : ""));
                        continue;
                    }
                    if (tokens.length < 2) {
                        errors.add(where + "colour '" + symbol + "' has no value; expected '" + symbol + " #rrggbb'");
                        continue;
                    }
                    int rgb;
                    try {
                        rgb = Colors.parseHex(tokens[1]);
                    } catch (IllegalArgumentException e) {
                        errors.add(where + e.getMessage());
                        continue;
                    }
                    if (rampName == null) {
                        errors.add(where + "colour '" + symbol + "' is outside a ramp; add 'ramp <name>' above it");
                        continue;
                    }
                    Integer first = symbolLines.putIfAbsent(symbol, lineNo);
                    if (first != null) {
                        errors.add(where + "symbol '" + symbol + "' is already used on line " + first);
                        continue;
                    }
                    String colourName = tokens.length > 2
                            ? String.join(" ", List.of(tokens).subList(2, tokens.length))
                            : "";
                    String owner = colourOwners.putIfAbsent(rgb, "'" + symbol + "'");
                    if (owner != null) {
                        warnings.add(where + "'" + symbol + "' has the same colour " + Colors.toHex(rgb)
                                + " as " + owner);
                    }
                    rampEntries.add(new Palette.Entry(symbol, rgb, colourName, rampName));
                }
            }
        }
        if (rampName != null) {
            ramps.add(new Palette.Ramp(rampName, rampOrdered, List.copyOf(rampEntries)));
            checkRamp(ramps.getLast(), rampHadColourLines, source, rampLine, errors, warnings);
        }
        if (errors.isEmpty() && ramps.isEmpty()) {
            errors.add(source + ": the palette has no colours");
        }
        if (!errors.isEmpty()) throw new ParseException(errors);
        return new Palette(name, ramps, warnings);
    }

    /** Empty ramps are errors; ordered ramps must get lighter at every step. */
    private static void checkRamp(Palette.Ramp ramp, boolean hadColourLines, String source, int line,
                                  List<String> errors, List<String> warnings) {
        String where = source + ":" + line + ": ";
        if (ramp.entries().isEmpty()) {
            if (!hadColourLines) errors.add(where + "ramp '" + ramp.name() + "' has no colours");
            return;
        }
        if (!ramp.ordered()) return;
        List<Palette.Entry> es = ramp.entries();
        for (int i = 1; i < es.size(); i++) {
            Palette.Entry prev = es.get(i - 1), cur = es.get(i);
            double step = Colors.lightness(cur.rgb()) - Colors.lightness(prev.rgb());
            if (step <= 0) {
                warnings.add(where + "ramp '" + ramp.name() + "': '" + cur.symbol() + "' is not lighter than '"
                        + prev.symbol() + "' (ramps go dark to light; add 'unordered' if intended)");
            } else if (step < MIN_LIGHTNESS_STEP) {
                warnings.add(where + "ramp '" + ramp.name() + "': '" + prev.symbol() + "' and '" + cur.symbol()
                        + "' are very close in lightness (step " + String.format(Locale.ROOT, "%.3f", step) + ")");
            }
        }
    }
}
