package pixelgen.parse;

import pixelgen.model.Effects;
import pixelgen.model.Grid;
import pixelgen.model.Palette;
import pixelgen.model.Sprite;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Parses and validates {@code .sprite} files.
 *
 * <pre>
 * # comment (a line whose first non-space character is #, also inside grids)
 * sprite farmer        optional; defaults to the file name
 * size 16 32           required: frame width and height (1-256)
 * palette farm         required: palette name or path to a .pal file
 * anchor 8 31          optional pivot point; defaults to bottom centre
 *
 * part head            a reusable piece; rows may be any (equal) width
 *   .KK.
 *   KssK
 * end
 *
 * frame idle           a full image, built from (all optional, in this order):
 *   ................     grid rows: exactly 'size' rows of 'size' width (otherwise starts empty)
 *   stamp head 6 0       paint a part with its top-left at (x, y); add flipx / flipy to mirror it
 *   recolor e>b E>B      swap symbols in the image so far (all at once, so a>b b>a swaps)
 *   outline K selective  add a 1 px outline around solid shapes (outer: all K; selective: see below)
 *   shadow ~ 12 3        a 12x3 ellipse behind the art, bottom row on the anchor (only fills empty cells)
 * end
 *
 * variant farmer_blue recolor e>b E>B    a recoloured copy of the whole sprite
 *
 * outline K            outline and shadow lines outside a frame apply to every frame,
 * shadow ~ 12 3        after the frame's own lines, in the order written
 * </pre>
 *
 * The file is read in one pass and checked afterwards, so the order of directives doesn't matter
 * (except inside a frame) and every problem is reported at once.
 * <p>
 * {@code outline <colour> selective} gives the outline on the lit top and left sides the darkest
 * shade of the colour it borders, and {@code <colour>} elsewhere; see {@link Effects.OutlineMode}.
 */
public final class SpriteParser {

    public static final int MAX_SIZE = 256;
    /** Unknown-symbol errors reported per grid before the rest are summarised. */
    public static final int MAX_SYMBOL_ERRORS_PER_GRID = 10;

    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_-]+");
    private static final Set<String> KEYWORDS =
            Set.of("sprite", "size", "palette", "anchor", "part", "frame", "variant", "end");
    private static final Set<String> FRAME_OPS = Set.of("stamp", "recolor", "outline", "shadow");
    /** Frame operations that may also stand outside a frame, applying to every frame. */
    private static final Set<String> SPRITE_EFFECTS = Set.of("outline", "shadow");
    private static final Set<String> REPEATABLE = Set.of("part", "frame", "variant", "end");

    private SpriteParser() {}

    private record Row(int line, String text) {}

    /** A step that changes the frame image, in the order written. */
    private sealed interface Op permits Stamp, Recolor, Outline, Shadow {
        int line();
    }

    private record Stamp(int line, String part, int x, int y, boolean flipX, boolean flipY) implements Op {}

    private record Recolor(int line, Map<Character, Character> map) implements Op {}

    private record Outline(int line, char symbol, Effects.OutlineMode mode) implements Op {}

    private record Shadow(int line, char symbol, int width, int height) implements Op {}

    private record Variant(int line, String name, Map<Character, Character> map) {}

    /** A part or frame as written, before it's checked. */
    private static final class Block {
        final String kind;
        final String name;
        final int line;
        final List<Row> rows = new ArrayList<>();
        final List<Op> ops = new ArrayList<>();
        boolean broken;  // a syntax error inside; skip size checks to avoid follow-on errors

        Block(String kind, String name, int line) {
            this.kind = kind;
            this.name = name;
            this.line = line;
        }

        boolean isPart() { return kind.equals("part"); }

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
        List<Variant> variants = new ArrayList<>();
        List<Op> spriteEffects = new ArrayList<>();  // outline/shadow lines outside frames
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
                    readBlockLine(open, tokens, line, lineNo, where, errors);
                    continue;
                }
            }

            if (!REPEATABLE.contains(tokens[0]) && KEYWORDS.contains(tokens[0])) {
                Integer first = directiveLines.putIfAbsent(tokens[0], lineNo);
                if (first != null) {
                    errors.add(where + "'" + tokens[0] + "' is already set on line " + first);
                    continue;
                }
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
                    int[] wh = ints(tokens, 1, 2, where, "size <width> <height>", errors);
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
                    anchor = ints(tokens, 1, 2, where, "anchor <x> <y>", errors);
                    anchorLine = lineNo;
                }
                case "part", "frame" -> {
                    if (tokens.length != 2 || !NAME.matcher(tokens[1]).matches()) {
                        errors.add(where + "expected '" + tokens[0] + " <name>' (letters, digits, _ and -)");
                    }
                    open = new Block(tokens[0], tokens.length > 1 ? tokens[1] : "?", lineNo);
                    blocks.add(open);
                }
                case "variant" -> {
                    if (tokens.length < 4 || !tokens[2].equals("recolor") || !NAME.matcher(tokens[1]).matches()) {
                        errors.add(where + "expected 'variant <name> recolor <from>><to> ...'");
                    } else {
                        Map<Character, Character> map = pairs(tokens, 3, where, errors);
                        if (map != null) variants.add(new Variant(lineNo, tokens[1], map));
                    }
                }
                case "end" -> errors.add(where + "'end' without a matching 'part' or 'frame'");
                case "outline", "shadow" -> {
                    Op op = parseOp(tokens, lineNo, where, errors);
                    if (op != null) spriteEffects.add(op);
                }
                default -> {
                    String hint = "";
                    if (FRAME_OPS.contains(tokens[0])) hint = "; '" + tokens[0] + "' lines belong inside a frame";
                    else if (tokens.length == 1) hint = "; grid rows belong between 'frame <name>' and 'end'";
                    errors.add(where + "unknown line '" + line + "'" + hint);
                }
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

        // Duplicates, then parts (frames need them), then frames.
        Map<String, Integer> firstLine = new HashMap<>();
        List<Block> unique = new ArrayList<>();
        for (Block b : blocks) {
            Integer first = firstLine.putIfAbsent(b.kind + ":" + b.name, b.line);
            if (first != null) {
                errors.add(source + ":" + b.line + ": " + b.label() + " is already defined on line " + first);
            } else {
                unique.add(b);
            }
        }
        Set<String> declaredParts = new LinkedHashSet<>();
        Map<String, Grid> parts = new LinkedHashMap<>();
        for (Block b : unique) {
            if (!b.isPart()) continue;
            declaredParts.add(b.name);
            Grid grid = checkRows(b, source, width, height, palette, errors);
            if (grid == null) continue;
            if (grid.isBlank()) warnings.add(source + ":" + b.line + ": " + b.label() + " has no visible pixels");
            parts.put(b.name, grid);
        }
        // Sprite-level effects: check each colour once, then apply them to every frame.
        List<Op> effects = new ArrayList<>();
        for (Op op : spriteEffects) {
            if (checkSymbol(effectSymbol(op), palette, source + ":" + op.line() + ": ", errors)) effects.add(op);
        }
        Map<Op, List<String>> clippedFrames = new LinkedHashMap<>();
        Map<String, Sprite.Frame> frames = new LinkedHashMap<>();
        for (Block b : unique) {
            if (b.isPart()) continue;
            Grid grid = buildFrame(b, source, width, height, palette, anchorX, anchorY, declaredParts, parts,
                    errors, warnings);
            if (grid == null) continue;
            for (Op op : effects) {
                Effects.Result r = applyEffect(op, grid, palette, anchorX, anchorY);
                if (r.clipped()) clippedFrames.computeIfAbsent(op, k -> new ArrayList<>()).add(b.name);
                grid = r.grid();
            }
            if (grid.isBlank()) warnings.add(source + ":" + b.line + ": " + b.label() + " has no visible pixels");
            frames.put(b.name, new Sprite.Frame(b.name, grid));
        }
        clippedFrames.forEach((op, names) -> warnings.add(source + ":" + op.line() + ": " + clipWarning(op)
                + " in frame" + (names.size() == 1 ? " " : "s ") + String.join(", ", names)));
        if (blocks.stream().noneMatch(b -> !b.isPart())) {
            errors.add(source + ": the sprite has no frames; add 'frame <name>' ... 'end'");
        }

        Map<String, Map<Character, Character>> variantMaps = new LinkedHashMap<>();
        Map<String, Integer> variantLines = new HashMap<>();
        for (Variant v : variants) {
            String where = source + ":" + v.line() + ": ";
            Integer first = variantLines.putIfAbsent(v.name(), v.line());
            if (first != null) {
                errors.add(where + "variant '" + v.name() + "' is already defined on line " + first);
            } else if (v.name().equals(name)) {
                errors.add(where + "a variant can't have the sprite's own name '" + name + "'");
            } else if (checkSymbols(v.map(), palette, where, errors)) {
                variantMaps.put(v.name(), v.map());
            }
        }

        if (!errors.isEmpty()) throw new ParseException(sortByLine(errors, source));
        return new Sprite(name, width, height, palette, anchorX, anchorY, parts, frames, variantMaps,
                sortByLine(warnings, source));
    }

    /** Reads one line inside a part or frame: a grid row or a frame operation. */
    private static void readBlockLine(Block b, String[] tokens, String line, int lineNo, String where,
                                      List<String> errors) {
        if (FRAME_OPS.contains(tokens[0]) && (tokens.length > 1 || SPRITE_EFFECTS.contains(tokens[0]))) {
            if (b.isPart()) {
                errors.add(where + b.label() + ": parts can't contain '" + tokens[0] + "' lines");
                b.broken = true;
                return;
            }
            Op op = parseOp(tokens, lineNo, where, errors);
            if (op == null) b.broken = true;
            else b.ops.add(op);
            return;
        }
        if (tokens.length > 1) {
            errors.add(where + b.label() + ": grid rows can't contain spaces");
            b.broken = true;
        } else if (!b.ops.isEmpty()) {
            if (!b.broken) {  // once per block, not for every row
                errors.add(where + b.label()
                        + ": grid rows must come before 'stamp', 'recolor', 'outline' and 'shadow' lines");
            }
            b.broken = true;
        } else {
            b.rows.add(new Row(lineNo, line));
        }
    }

    /** Parses one frame operation line (its first token is in FRAME_OPS); null after an error. */
    private static Op parseOp(String[] tokens, int lineNo, String where, List<String> errors) {
        return switch (tokens[0]) {
            case "stamp" -> parseStamp(tokens, lineNo, where, errors);
            case "recolor" -> {
                Map<Character, Character> map = pairs(tokens, 1, where, errors);
                yield map == null ? null : new Recolor(lineNo, map);
            }
            case "outline" -> parseOutline(tokens, lineNo, where, errors);
            default -> parseShadow(tokens, lineNo, where, errors);
        };
    }

    /** {@code outline <colour> [outer|selective]} */
    private static Outline parseOutline(String[] tokens, int lineNo, String where, List<String> errors) {
        String form = "outline <colour> [outer|selective]";
        if (tokens.length < 2 || tokens.length > 3 || !isSymbol(tokens[1])) {
            errors.add(where + "expected '" + form + "', like 'outline K'");
            return null;
        }
        Effects.OutlineMode mode = Effects.OutlineMode.OUTER;
        if (tokens.length == 3) {
            switch (tokens[2]) {
                case "outer" -> mode = Effects.OutlineMode.OUTER;
                case "selective" -> mode = Effects.OutlineMode.SELECTIVE;
                default -> {
                    errors.add(where + "unknown outline mode '" + tokens[2] + "'; expected outer or selective");
                    return null;
                }
            }
        }
        return new Outline(lineNo, tokens[1].charAt(0), mode);
    }

    /** {@code shadow <colour> <width> <height>} */
    private static Shadow parseShadow(String[] tokens, int lineNo, String where, List<String> errors) {
        String form = "shadow <colour> <width> <height>";
        if (tokens.length != 4 || !isSymbol(tokens[1])) {
            errors.add(where + "expected '" + form + "', like 'shadow ~ 12 3'");
            return null;
        }
        int[] wh = ints(tokens, 2, 2, where, form, errors);
        if (wh == null) return null;
        if (wh[0] < 1 || wh[1] < 1 || wh[0] > MAX_SIZE || wh[1] > MAX_SIZE) {
            errors.add(where + "shadow size must be between 1 and " + MAX_SIZE);
            return null;
        }
        return new Shadow(lineNo, tokens[1].charAt(0), wh[0], wh[1]);
    }

    /** A single non-transparent character; whether the palette has it is checked later. */
    private static boolean isSymbol(String token) {
        return token.length() == 1 && token.charAt(0) != Palette.TRANSPARENT;
    }

    /** Draws an outline or shadow; its colour has already been checked. */
    private static Effects.Result applyEffect(Op op, Grid grid, Palette palette, int anchorX, int anchorY) {
        return switch (op) {
            case Outline o -> Effects.outline(grid, palette, o.symbol(), o.mode());
            case Shadow s -> Effects.shadow(grid, s.symbol(), s.width(), s.height(), anchorX, anchorY);
            default -> throw new IllegalArgumentException("not an effect: " + op);
        };
    }

    private static char effectSymbol(Op op) {
        return switch (op) {
            case Outline o -> o.symbol();
            case Shadow s -> s.symbol();
            default -> throw new IllegalArgumentException("not an effect: " + op);
        };
    }

    private static String clipWarning(Op op) {
        return op instanceof Shadow s
                ? "the " + s.width() + "x" + s.height() + " shadow is cut off at the frame edge"
                : "the outline is cut off at the frame edge; leave 1 px free around the art";
    }

    /** {@code stamp <part> <x> <y> [flipx] [flipy]} */
    private static Stamp parseStamp(String[] tokens, int lineNo, String where, List<String> errors) {
        String form = "stamp <part> <x> <y> [flipx] [flipy]";
        if (tokens.length < 4) {
            errors.add(where + "expected '" + form + "'");
            return null;
        }
        int[] xy = ints(tokens, 2, 2, where, form, errors);
        if (xy == null) return null;
        boolean fx = false, fy = false;
        for (int i = 4; i < tokens.length; i++) {
            switch (tokens[i]) {
                case "flipx" -> fx = true;
                case "flipy" -> fy = true;
                default -> {
                    errors.add(where + "unknown stamp option '" + tokens[i] + "'; expected flipx or flipy");
                    return null;
                }
            }
        }
        return new Stamp(lineNo, tokens[1], xy[0], xy[1], fx, fy);
    }

    /** Parses {@code a>b c>d ...} from {@code tokens[start]} on. Symbols are checked later. */
    private static Map<Character, Character> pairs(String[] tokens, int start, String where, List<String> errors) {
        Map<Character, Character> map = new LinkedHashMap<>();
        for (int i = start; i < tokens.length; i++) {
            String t = tokens[i];
            if (t.length() != 3 || t.charAt(1) != '>') {
                errors.add(where + "'" + t + "' is not a colour swap; expected '<from>><to>', like e>b");
                return null;
            }
            char from = t.charAt(0), to = t.charAt(2);
            if (from == Palette.TRANSPARENT) {
                errors.add(where + "'" + t + "': transparent pixels can't be recoloured");
                return null;
            }
            if (map.put(from, to) != null) {
                errors.add(where + "'" + from + "' is recoloured twice");
                return null;
            }
        }
        if (map.isEmpty()) {
            errors.add(where + "expected at least one colour swap, like e>b");
            return null;
        }
        return map;
    }

    /** A frame's own outline or shadow line: checks the colour, draws, warns when cut off. */
    private static Grid effect(Op op, Grid grid, Palette palette, int anchorX, int anchorY, String where,
                               List<String> errors, List<String> warnings) {
        if (!checkSymbol(effectSymbol(op), palette, where, errors)) return null;
        if (grid == null) return null;
        Effects.Result r = applyEffect(op, grid, palette, anchorX, anchorY);
        if (r.clipped()) warnings.add(where + clipWarning(op));
        return r.grid();
    }

    private static boolean checkSymbol(char symbol, Palette palette, String where, List<String> errors) {
        if (palette == null) return false;
        if (palette.contains(symbol)) return true;
        errors.add(where + "'" + symbol + "' is not in palette '" + palette.name() + "'");
        return false;
    }

    /** Both sides of each swap must be palette symbols ('.' is allowed as a target). */
    private static boolean checkSymbols(Map<Character, Character> map, Palette palette, String where,
                                        List<String> errors) {
        if (palette == null) return false;
        boolean ok = true;
        for (Map.Entry<Character, Character> e : map.entrySet()) {
            if (!palette.contains(e.getKey())) {
                errors.add(where + "'" + e.getKey() + "' is not in palette '" + palette.name() + "'");
                ok = false;
            }
            char to = e.getValue();
            if (to != Palette.TRANSPARENT && !palette.contains(to)) {
                errors.add(where + "'" + to + "' is not in palette '" + palette.name() + "'");
                ok = false;
            }
        }
        return ok;
    }

    /** Builds a frame from its rows (or a blank image) and its operations; null on errors. */
    private static Grid buildFrame(Block b, String source, int width, int height, Palette palette,
                                   int anchorX, int anchorY, Set<String> declaredParts, Map<String, Grid> parts,
                                   List<String> errors, List<String> warnings) {
        if (b.rows.isEmpty() && b.ops.isEmpty()) {
            if (!b.broken) {
                errors.add(source + ":" + b.line + ": " + b.label() + " is empty; add grid rows or 'stamp' lines");
            }
            return null;
        }
        int before = errors.size();
        Grid grid = null;
        if (!b.rows.isEmpty()) {
            grid = checkRows(b, source, width, height, palette, errors);
        } else if (width > 0 && height > 0) {
            grid = Grid.blank(width, height);
        }
        for (Op op : b.ops) {
            String where = source + ":" + op.line() + ": ";
            switch (op) {
                case Stamp s -> {
                    Grid part = parts.get(s.part());
                    if (part == null) {
                        if (!declaredParts.contains(s.part())) {  // a declared part with errors is already reported
                            errors.add(where + b.label() + ": unknown part '" + s.part() + "'"
                                    + (declaredParts.isEmpty() ? " (no parts are defined)"
                                    : " (parts: " + String.join(", ", declaredParts) + ")"));
                        }
                        grid = null;
                        continue;
                    }
                    if (s.flipX()) part = part.flipX();
                    if (s.flipY()) part = part.flipY();
                    if (grid == null) continue;
                    if (grid.clips(part, s.x(), s.y())) {
                        warnings.add(where + b.label() + ": part '" + s.part() + "' at (" + s.x() + "," + s.y()
                                + ") is partly outside the frame and was cut off");
                    }
                    grid = grid.stamp(part, s.x(), s.y());
                }
                case Recolor r -> {
                    if (!checkSymbols(r.map(), palette, where, errors)) grid = null;
                    if (grid != null) grid = grid.recolor(r.map());
                }
                case Outline o -> grid = effect(o, grid, palette, anchorX, anchorY, where + b.label() + ": ",
                        errors, warnings);
                case Shadow s -> grid = effect(s, grid, palette, anchorX, anchorY, where + b.label() + ": ",
                        errors, warnings);
            }
        }
        if (b.broken || errors.size() > before) return null;
        return grid;
    }

    /** Checks the grid rows of a part or frame and returns them as a grid, or null on errors. */
    private static Grid checkRows(Block b, String source, int width, int height, Palette palette,
                                  List<String> errors) {
        int before = errors.size();
        if (b.rows.isEmpty()) {
            if (!b.broken) errors.add(source + ":" + b.line + ": " + b.label() + " has no rows");
            return null;
        }
        if (!b.broken) {
            boolean isFrame = !b.isPart();
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
        if (b.broken || errors.size() > before || palette == null) return null;
        return new Grid(b.rows.stream().map(Row::text).toList());
    }

    /** Orders messages by their line number; messages about the whole file come first. Stable. */
    private static List<String> sortByLine(List<String> messages, String source) {
        String prefix = source + ":";
        return messages.stream().sorted(Comparator.comparingInt(m -> {
            if (!m.startsWith(prefix)) return 0;
            int i = prefix.length(), j = i;
            while (j < m.length() && Character.isDigit(m.charAt(j))) j++;
            return j > i ? Integer.parseInt(m.substring(i, j)) : 0;
        })).toList();
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

    /** Parses {@code count} integers starting at {@code tokens[start]}, or records an error and returns null. */
    private static int[] ints(String[] tokens, int start, int count, String where, String form, List<String> errors) {
        if (start == 1 && tokens.length != count + 1) {
            errors.add(where + "expected '" + form + "'");
            return null;
        }
        int[] values = new int[count];
        for (int i = 0; i < count; i++) {
            try {
                values[i] = Integer.parseInt(tokens[start + i]);
            } catch (NumberFormatException e) {
                errors.add(where + "'" + tokens[start + i] + "' is not a whole number; expected '" + form + "'");
                return null;
            }
        }
        return values;
    }
}
