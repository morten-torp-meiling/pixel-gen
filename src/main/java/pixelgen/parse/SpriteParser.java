package pixelgen.parse;

import pixelgen.model.Grid;
import pixelgen.model.Palette;
import pixelgen.model.Sprite;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Parses and validates {@code .sprite} files.
 *
 * <pre>
 * # comment (a line whose first non-space character is #, also inside grids)
 * sprite apple        optional; defaults to the file name
 * size 16 16          required: frame width and height (1-256)
 * palette farm        required: palette name or path to a .pal file
 * anchor 8 15         optional pivot point; defaults to bottom centre
 *
 * part leaf           a reusable piece; rows may be any (equal) width
 *   .gg
 *   gG.
 * end
 *
 * frame idle          a full image; exactly 'size' rows of 'size' width
 *   ................
 *   ...
 * end
 * </pre>
 *
 * The file is read in one pass and checked afterwards, so the order of directives doesn't matter
 * and every problem is reported at once.
 */
public final class SpriteParser {

    public static final int MAX_SIZE = 256;
    /** Unknown-symbol errors reported per grid before the rest are summarised. */
    public static final int MAX_SYMBOL_ERRORS_PER_GRID = 10;

    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_-]+");
    private static final Set<String> KEYWORDS = Set.of("sprite", "size", "palette", "anchor", "part", "frame", "end");

    private SpriteParser() {}

    private record Row(int line, String text) {}

    /** A part or frame as written, before it's checked. */
    private static final class Block {
        final String kind;
        final String name;
        final int line;
        final List<Row> rows = new ArrayList<>();
        boolean broken;  // a row had spaces; skip the size checks to avoid follow-on errors

        Block(String kind, String name, int line) {
            this.kind = kind;
            this.name = name;
            this.line = line;
        }

        String label() { return kind + " '" + name + "'"; }
    }

    public static Sprite parseFile(Path file, String displayName, PaletteResolver palettes)
            throws IOException, ParseException {
        String fileName = file.getFileName().toString();
        String defaultName = fileName.endsWith(".sprite")
                ? fileName.substring(0, fileName.length() - ".sprite".length())
                : fileName;
        return parse(Files.readString(file, StandardCharsets.UTF_8), displayName, defaultName, palettes);
    }

    public static Sprite parse(String text, String source, String defaultName, PaletteResolver palettes)
            throws ParseException {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Map<String, Integer> directiveLines = new HashMap<>();

        String name = defaultName;
        int width = -1, height = -1;
        String paletteName = null;
        int paletteLine = 0;
        int[] anchor = null;
        int anchorLine = 0;
        List<Block> blocks = new ArrayList<>();
        Block open = null;

        String[] lines = text.split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            int lineNo = i + 1;
            String line = lines[i].strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String where = source + ":" + lineNo + ": ";
            String[] tokens = line.split("\\s+");

            if (open != null) {
                if (line.equals("end")) {
                    open = null;
                    continue;
                }
                if (tokens.length > 1 && KEYWORDS.contains(tokens[0])) {
                    errors.add(source + ":" + open.line + ": " + open.label() + " has no 'end' before line " + lineNo);
                    open = null;
                    // fall through and read this line as a directive
                } else {
                    if (tokens.length > 1) {
                        errors.add(where + open.label() + ": grid rows can't contain spaces");
                        open.broken = true;
                    } else {
                        open.rows.add(new Row(lineNo, line));
                    }
                    continue;
                }
            }

            if (!directiveLines.containsKey(tokens[0]) || tokens[0].equals("part") || tokens[0].equals("frame")) {
                directiveLines.putIfAbsent(tokens[0], lineNo);
            } else if (!tokens[0].equals("end") && KEYWORDS.contains(tokens[0])) {
                errors.add(where + "'" + tokens[0] + "' is already set on line " + directiveLines.get(tokens[0]));
                continue;
            }

            switch (tokens[0]) {
                case "sprite" -> {
                    if (tokens.length != 2 || !NAME.matcher(tokens[1]).matches()) {
                        errors.add(where + "expected 'sprite <name>' (letters, digits, _ and -)");
                    } else {
                        name = tokens[1];
                    }
                }
                case "size" -> {
                    int[] wh = ints(tokens, 2, where, "size <width> <height>", errors);
                    if (wh != null) {
                        if (wh[0] < 1 || wh[1] < 1 || wh[0] > MAX_SIZE || wh[1] > MAX_SIZE) {
                            errors.add(where + "size must be between 1 and " + MAX_SIZE);
                        } else {
                            width = wh[0];
                            height = wh[1];
                        }
                    }
                }
                case "palette" -> {
                    if (tokens.length != 2) {
                        errors.add(where + "expected 'palette <name or file.pal>'");
                    } else {
                        paletteName = tokens[1];
                        paletteLine = lineNo;
                    }
                }
                case "anchor" -> {
                    anchor = ints(tokens, 2, where, "anchor <x> <y>", errors);
                    anchorLine = lineNo;
                }
                case "part", "frame" -> {
                    if (tokens.length != 2 || !NAME.matcher(tokens[1]).matches()) {
                        errors.add(where + "expected '" + tokens[0] + " <name>' (letters, digits, _ and -)");
                    }
                    open = new Block(tokens[0], tokens.length > 1 ? tokens[1] : "?", lineNo);
                    blocks.add(open);
                }
                case "end" -> errors.add(where + "'end' without a matching 'part' or 'frame'");
                default -> errors.add(where + "unknown line '" + line + "'"
                        + (tokens.length == 1 ? "; grid rows belong between 'frame <name>' and 'end'" : ""));
            }
        }
        if (open != null) {
            errors.add(source + ":" + open.line + ": " + open.label() + " has no 'end'");
        }

        // ---- checks that need the whole file ----
        if (width < 0 && !directiveLines.containsKey("size")) errors.add(source + ": missing 'size <width> <height>'");
        Palette palette = null;
        if (paletteName == null) {
            if (!directiveLines.containsKey("palette")) errors.add(source + ": missing 'palette <name>'");
        } else {
            palette = loadPalette(paletteName, source + ":" + paletteLine + ": ", palettes, errors);
        }

        int anchorX = width > 0 ? width / 2 : 0;
        int anchorY = height > 0 ? height - 1 : 0;
        if (anchor != null) {
            if (width > 0 && (anchor[0] < 0 || anchor[1] < 0 || anchor[0] >= width || anchor[1] >= height)) {
                errors.add(source + ":" + anchorLine + ": anchor (" + anchor[0] + "," + anchor[1]
                        + ") is outside the " + width + "x" + height + " frame");
            }
            anchorX = anchor[0];
            anchorY = anchor[1];
        }

        Map<String, Grid> parts = new LinkedHashMap<>();
        Map<String, Sprite.Frame> frames = new LinkedHashMap<>();
        Map<String, Integer> firstLine = new HashMap<>();
        for (Block b : blocks) {
            Integer first = firstLine.putIfAbsent(b.kind + ":" + b.name, b.line);
            if (first != null) {
                errors.add(source + ":" + b.line + ": " + b.label() + " is already defined on line " + first);
                continue;
            }
            Grid grid = checkBlock(b, source, width, height, palette, errors);
            if (grid == null) continue;
            if (grid.isBlank()) warnings.add(source + ":" + b.line + ": " + b.label() + " has no visible pixels");
            if (b.kind.equals("part")) parts.put(b.name, grid);
            else frames.put(b.name, new Sprite.Frame(b.name, grid));
        }
        if (blocks.stream().noneMatch(b -> b.kind.equals("frame"))) {
            errors.add(source + ": the sprite has no frames; add 'frame <name>' ... 'end'");
        }

        if (!errors.isEmpty()) throw new ParseException(sortByLine(errors, source));
        return new Sprite(name, width, height, palette, anchorX, anchorY, parts, frames, sortByLine(warnings, source));
    }

    /** Orders messages by their line number; messages about the whole file come first. Stable. */
    private static List<String> sortByLine(List<String> messages, String source) {
        String prefix = source + ":";
        return messages.stream().sorted(java.util.Comparator.comparingInt(m -> {
            if (!m.startsWith(prefix)) return 0;
            int i = prefix.length(), j = i;
            while (j < m.length() && Character.isDigit(m.charAt(j))) j++;
            return j > i ? Integer.parseInt(m.substring(i, j)) : 0;
        })).toList();
    }

    /** Checks one part or frame and returns its grid, or null if it has errors. */
    private static Grid checkBlock(Block b, String source, int width, int height, Palette palette,
                                   List<String> errors) {
        int before = errors.size();
        if (b.rows.isEmpty()) {
            if (!b.broken) errors.add(source + ":" + b.line + ": " + b.label() + " has no rows");
            return null;
        }
        if (!b.broken) {
            boolean isFrame = b.kind.equals("frame");
            int expected = isFrame ? width : b.rows.getFirst().text().length();
            if (expected > 0) {
                for (int y = 0; y < b.rows.size(); y++) {
                    Row r = b.rows.get(y);
                    if (r.text().length() != expected) {
                        errors.add(source + ":" + r.line() + ": " + b.label() + " row " + y + " is "
                                + r.text().length() + " wide; "
                                + (isFrame ? "size is " + width + "x" + height : "the first row is " + expected));
                    }
                }
            }
            if (isFrame && height > 0 && b.rows.size() != height) {
                errors.add(source + ":" + b.line + ": " + b.label() + " has " + b.rows.size()
                        + " rows; size is " + width + "x" + height);
            }
        }
        if (palette != null) {
            int unknown = 0;
            for (int y = 0; y < b.rows.size(); y++) {
                Row r = b.rows.get(y);
                for (int x = 0; x < r.text().length(); x++) {
                    char c = r.text().charAt(x);
                    if (c == Palette.TRANSPARENT || palette.contains(c)) continue;
                    if (++unknown <= MAX_SYMBOL_ERRORS_PER_GRID) {
                        errors.add(source + ":" + r.line() + ": " + b.label() + " pixel (" + x + "," + y + "): '"
                                + c + "' is not in palette '" + palette.name() + "'");
                    }
                }
            }
            if (unknown > MAX_SYMBOL_ERRORS_PER_GRID) {
                // Last row's line, so the summary sorts after the errors it summarises.
                errors.add(source + ":" + b.rows.getLast().line() + ": " + b.label() + ": "
                        + (unknown - MAX_SYMBOL_ERRORS_PER_GRID) + " more unknown symbols not listed");
            }
        }
        if (b.broken || errors.size() > before) return null;
        return new Grid(b.rows.stream().map(Row::text).toList());
    }

    private static Palette loadPalette(String nameOrPath, String where, PaletteResolver palettes,
                                       List<String> errors) {
        try {
            return palettes.load(nameOrPath);
        } catch (NoSuchFileException e) {
            errors.add(where + "palette '" + nameOrPath + "' not found (" + e.getFile() + ")");
        } catch (ParseException e) {
            // One multi-line message, so sorting by line keeps the palette's errors together.
            errors.add(where + "palette '" + nameOrPath + "' has errors:\n  " + String.join("\n  ", e.errors()));
        } catch (IOException e) {
            errors.add(where + "cannot read palette '" + nameOrPath + "': " + e.getMessage());
        }
        return null;
    }

    /** Parses exactly {@code count} integer arguments, or records an error and returns null. */
    private static int[] ints(String[] tokens, int count, String where, String form, List<String> errors) {
        if (tokens.length != count + 1) {
            errors.add(where + "expected '" + form + "'");
            return null;
        }
        int[] values = new int[count];
        for (int i = 0; i < count; i++) {
            try {
                values[i] = Integer.parseInt(tokens[i + 1]);
            } catch (NumberFormatException e) {
                errors.add(where + "'" + tokens[i + 1] + "' is not a whole number; expected '" + form + "'");
                return null;
            }
        }
        return values;
    }
}
