package pixelgen.parse;

import pixelgen.model.Anim;
import pixelgen.model.Effects;
import pixelgen.model.Grid;
import pixelgen.model.Palette;
import pixelgen.model.Sprite;
import pixelgen.model.Terrain;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
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
 * frame left_0 from right_0 flipx        a frame derived from another, with no rows or 'end';
 * frame bob from idle shift 0 -1         options in any order: flipx, flipy, shift <dx> <dy>,
 *                                        recolor <from>><to> ...
 *
 * anim walk_down fps 8 loop: down_1 down_0 down_2 down_0    an animation; 'once:' stops at the end;
 * anim swing fps 10 once: swing_0 swing_1*2 swing_2         frame*2 shows a frame for 2 ticks
 *
 * variant farmer_blue recolor e>b E>B    a recoloured copy of the whole sprite
 *
 * outline K            outline and shadow lines outside a frame apply to every frame,
 * shadow ~ 12 3        after the frame's own lines, in the order written
 *
 * terrain grass_dirt   where two grounds meet (see {@link Terrain}):
 *   raised grass_tex     the higher ground's texture (a frame-sized part); the profiles describe it
 *   lower dirt_tex       the lower ground's texture (a frame-sized part)
 *   bank G               optional: raised ground with lower ground below or right of it
 *   lip L                optional: raised ground with lower ground above or left of it
 *   shadow D             optional: lower ground just below or right of raised ground
 *   n 5 5 6 6 ...        a profile: per column (n, s) or row (w, e), how far the raised ground
 *   n2 5 5 4 4 ...       reaches in from that side; any name starting with n, s, w or e
 * end
 *
 * frame dirt_corner_nw
 *   blend grass_dirt n|w   the whole frame from a terrain: raised ground where any profile
 * end                      reaches (|), or where all of them do (&); must come first
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
    /** Highest animation rate accepted, in frames per second. */
    public static final int MAX_FPS = 120;
    /** Unknown-symbol errors reported per grid before the rest are summarised. */
    public static final int MAX_SYMBOL_ERRORS_PER_GRID = 10;

    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_-]+");
    private static final Set<String> KEYWORDS =
            Set.of("sprite", "size", "palette", "anchor", "part", "frame", "variant", "anim", "terrain", "end");
    private static final Set<String> FRAME_OPS = Set.of("stamp", "recolor", "outline", "shadow", "blend");
    /** Frame operations that may also stand outside a frame, applying to every frame. */
    private static final Set<String> SPRITE_EFFECTS = Set.of("outline", "shadow");
    private static final Set<String> REPEATABLE = Set.of("part", "frame", "variant", "anim", "terrain", "end");
    private static final Pattern PROFILE = Pattern.compile("[nswe][A-Za-z0-9_-]*");

    private SpriteParser() {}

    private record Row(int line, String text) {}

    /** A step that changes the frame image, in the order written. */
    private sealed interface Op permits Stamp, Recolor, Outline, Shadow, Blend {
        int line();
    }

    /** {@code blend <terrain> a|b} or {@code a&b}: the whole frame made from a terrain. */
    private record Blend(int line, String terrain, List<String> profiles, boolean union) implements Op {}

    /** A {@code terrain} block as written, before it's checked. */
    private static final class TerrainDef {
        final String name;
        final int line;
        final Map<String, String[]> settings = new LinkedHashMap<>();   // raised, lower, bank, lip, shadow
        final Map<String, Integer> settingLines = new HashMap<>();
        final Map<String, String[]> profiles = new LinkedHashMap<>();  // name -> its numbers, as written
        final Map<String, Integer> profileLines = new HashMap<>();

        TerrainDef(String name, int line) {
            this.name = name;
            this.line = line;
        }
    }

    private record Stamp(int line, String part, int x, int y, boolean flipX, boolean flipY) implements Op {}

    private record Recolor(int line, Map<Character, Character> map) implements Op, Transform {}

    private record Outline(int line, char symbol, Effects.OutlineMode mode) implements Op {}

    private record Shadow(int line, char symbol, int width, int height) implements Op {}

    private record Variant(int line, String name, Map<Character, Character> map) {}

    private record AnimDef(int line, Anim anim) {}

    /** A change made to a derived frame's source image, in the order written. */
    private sealed interface Transform permits Flip, Move, Recolor {}

    private record Flip(boolean horizontal) implements Transform {}

    private record Move(int dx, int dy) implements Transform {}

    /** A part or frame as written, before it's checked. */
    private static final class Block {
        final String kind;
        final String name;
        final int line;
        final List<Row> rows = new ArrayList<>();
        final List<Op> ops = new ArrayList<>();
        String from;  // derived frames: the frame they copy
        final List<Transform> transforms = new ArrayList<>();
        boolean broken;  // a syntax error inside; skip size checks to avoid follow-on errors

        Block(String kind, String name, int line) {
            this.kind = kind;
            this.name = name;
            this.line = line;
        }

        boolean isPart() { return kind.equals("part"); }

        boolean isDerived() { return from != null; }

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
        List<AnimDef> animDefs = new ArrayList<>();
        Block open = null;
        List<TerrainDef> terrainDefs = new ArrayList<>();
        TerrainDef openTerrain = null;

        String[] lines = text.split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            int lineNo = i + 1;
            String line = lines[i].strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String where = source + ":" + lineNo + ": ";
            String[] tokens = line.split("\\s+");

            if (openTerrain != null) {
                if (line.equals("end")) {
                    openTerrain = null;
                    continue;
                }
                if (tokens.length > 1 && KEYWORDS.contains(tokens[0])) {
                    errors.add(source + ":" + openTerrain.line + ": terrain '" + openTerrain.name
                            + "' has no 'end' before line " + lineNo);
                    openTerrain = null;
                    // fall through and read this line as a directive
                } else {
                    readTerrainLine(openTerrain, tokens, lineNo, where, errors);
                    continue;
                }
            }

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
                case "anim" -> {
                    Anim a = parseAnim(tokens, where, errors);
                    if (a != null) animDefs.add(new AnimDef(lineNo, a));
                }
                case "part", "frame" -> {
                    if (tokens[0].equals("frame") && tokens.length >= 3 && tokens[2].equals("from")) {
                        blocks.add(parseDerived(tokens, lineNo, where, errors));
                        continue;
                    }
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
                case "terrain" -> {
                    if (tokens.length != 2 || !NAME.matcher(tokens[1]).matches()) {
                        errors.add(where + "expected 'terrain <name>' (letters, digits, _ and -)");
                    }
                    openTerrain = new TerrainDef(tokens.length > 1 ? tokens[1] : "?", lineNo);
                    terrainDefs.add(openTerrain);
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
        if (openTerrain != null) {
            errors.add(source + ":" + openTerrain.line + ": terrain '" + openTerrain.name + "' has no 'end'");
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
        // Terrains, which frames can blend.
        Set<String> declaredTerrains = new LinkedHashSet<>();
        Map<String, Terrain> terrains = new LinkedHashMap<>();
        Map<String, Integer> terrainLines = new HashMap<>();
        for (TerrainDef def : terrainDefs) {
            Integer first = terrainLines.putIfAbsent(def.name, def.line);
            if (first != null) {
                errors.add(source + ":" + def.line + ": terrain '" + def.name + "' is already defined on line " + first);
                continue;
            }
            declaredTerrains.add(def.name);
            Terrain t = checkTerrain(def, source, width, height, palette, declaredParts, parts, errors, warnings);
            if (t != null) terrains.put(def.name, t);
        }
        // Sprite-level effects: check each colour once, then apply them to every frame.
        List<Op> effects = new ArrayList<>();
        for (Op op : spriteEffects) {
            if (checkSymbol(effectSymbol(op), palette, source + ":" + op.line() + ": ", errors)) effects.add(op);
        }
        // Frames: drawn ones first, then derived ones (which may copy each other), all before
        // sprite-level effects, so a flipped frame gets its outline and shadow drawn afresh.
        Map<String, Block> frameBlocks = new LinkedHashMap<>();
        for (Block b : unique) if (!b.isPart()) frameBlocks.put(b.name, b);
        Map<String, Grid> built = new HashMap<>();
        for (Block b : frameBlocks.values()) {
            if (b.isDerived()) continue;
            Grid grid = buildFrame(b, source, width, height, palette, anchorX, anchorY, declaredParts, parts,
                    declaredTerrains, terrains, errors, warnings);
            if (grid != null) built.put(b.name, grid);
        }
        Derivation derivation = new Derivation(source, palette, frameBlocks, declaredParts, built, errors, warnings);
        for (Block b : frameBlocks.values()) if (b.isDerived()) derivation.derive(b);

        Map<Op, List<String>> clippedFrames = new LinkedHashMap<>();
        Map<String, Sprite.Frame> frames = new LinkedHashMap<>();
        for (Block b : frameBlocks.values()) {
            Grid grid = built.get(b.name);
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

        Map<String, Anim> anims = new LinkedHashMap<>();
        Map<String, Integer> animLines = new HashMap<>();
        for (AnimDef def : animDefs) {
            Anim a = def.anim();
            String where = source + ":" + def.line() + ": ";
            Integer first = animLines.putIfAbsent(a.name(), def.line());
            if (first != null) {
                errors.add(where + "anim '" + a.name() + "' is already defined on line " + first);
                continue;
            }
            Set<String> unknown = new LinkedHashSet<>();
            for (Anim.Step step : a.steps()) {
                if (!frameBlocks.containsKey(step.frame())) unknown.add(step.frame());
            }
            for (String u : unknown) {
                errors.add(where + "anim '" + a.name() + "': no frame '" + u + "'" + framesHint(frameBlocks, declaredParts, u));
            }
            if (unknown.isEmpty()) anims.put(a.name(), a);
        }

        if (!errors.isEmpty()) throw new ParseException(sortByLine(errors, source));
        return new Sprite(name, width, height, palette, anchorX, anchorY, parts, frames, anims, variantMaps,
                sortByLine(warnings, source));
    }

    /** " (frames: a, b)", or a hint that the name is a part. */
    private static String framesHint(Map<String, Block> frameBlocks, Set<String> parts, String name) {
        if (parts.contains(name)) return " ('" + name + "' is a part; frames can use it with 'stamp')";
        return frameBlocks.isEmpty() ? " (no frames are defined)"
                : " (frames: " + String.join(", ", frameBlocks.keySet()) + ")";
    }

    /** Builds derived frames from their sources, following chains and catching loops. */
    private static final class Derivation {
        final String source;
        final Palette palette;
        final Map<String, Block> frameBlocks;
        final Set<String> parts;
        final Map<String, Grid> built;
        final List<String> errors, warnings;
        final Set<String> done = new HashSet<>();
        final List<String> visiting = new ArrayList<>();

        Derivation(String source, Palette palette, Map<String, Block> frameBlocks, Set<String> parts,
                   Map<String, Grid> built, List<String> errors, List<String> warnings) {
            this.source = source;
            this.palette = palette;
            this.frameBlocks = frameBlocks;
            this.parts = parts;
            this.built = built;
            this.errors = errors;
            this.warnings = warnings;
        }

        /** The derived frame's image (also stored in {@code built}), or null after an error. */
        Grid derive(Block b) {
            if (done.contains(b.name)) return built.get(b.name);
            String where = source + ":" + b.line + ": " + b.label() + ": ";
            if (visiting.contains(b.name)) {
                List<String> loop = new ArrayList<>(visiting.subList(visiting.indexOf(b.name), visiting.size()));
                loop.add(b.name);
                errors.add(where + "frames can't derive from each other in a loop (" + String.join(" -> ", loop) + ")");
                return null;
            }
            if (b.broken) {
                done.add(b.name);
                return null;
            }
            visiting.add(b.name);
            Block src = frameBlocks.get(b.from);
            Grid grid;
            if (src == null) {
                errors.add(where + "no frame '" + b.from + "' to derive from" + framesHint(frameBlocks, parts, b.from));
                grid = null;
            } else {
                grid = src.isDerived() ? derive(src) : built.get(src.name);
            }
            for (Transform t : b.transforms) {
                switch (t) {
                    case Flip f -> {
                        if (grid != null) grid = f.horizontal() ? grid.flipX() : grid.flipY();
                    }
                    case Move m -> {
                        if (grid == null) continue;
                        if (grid.shiftClips(m.dx(), m.dy())) {
                            warnings.add(where + "shift " + m.dx() + " " + m.dy()
                                    + " moves pixels out of the frame; they are cut off");
                        }
                        grid = grid.shift(m.dx(), m.dy());
                    }
                    case Recolor r -> {
                        if (!checkSymbols(r.map(), palette, source + ":" + b.line + ": ", errors)) grid = null;
                        if (grid != null) grid = grid.recolor(r.map());
                    }
                }
            }
            visiting.removeLast();
            done.add(b.name);
            if (grid != null) built.put(b.name, grid);
            return grid;
        }
    }

    /** {@code frame <name> from <frame> [flipx] [flipy] [shift <dx> <dy>] [recolor <a>b> ...]}, in any order. */
    private static Block parseDerived(String[] tokens, int lineNo, String where, List<String> errors) {
        String form = "frame <name> from <frame> [flipx] [flipy] [shift <dx> <dy>] [recolor <from>><to> ...]";
        Block b = new Block("frame", tokens[1], lineNo);
        b.from = tokens.length > 3 ? tokens[3] : "?";
        if (!NAME.matcher(tokens[1]).matches() || tokens.length < 4 || !NAME.matcher(tokens[3]).matches()) {
            errors.add(where + "expected '" + form + "'");
            b.broken = true;
            return b;
        }
        int i = 4;
        while (i < tokens.length) {
            switch (tokens[i]) {
                case "flipx", "flipy" -> {
                    b.transforms.add(new Flip(tokens[i].equals("flipx")));
                    i++;
                }
                case "shift" -> {
                    int[] d = i + 2 < tokens.length ? ints(tokens, i + 1, 2, where, form, errors) : null;
                    if (i + 2 >= tokens.length) errors.add(where + "expected 'shift <dx> <dy>'");
                    if (d == null) {
                        b.broken = true;
                        return b;
                    }
                    b.transforms.add(new Move(d[0], d[1]));
                    i += 3;
                }
                case "recolor" -> {
                    int j = i + 1;
                    while (j < tokens.length && tokens[j].length() == 3 && tokens[j].charAt(1) == '>') j++;
                    Map<Character, Character> map = pairs(Arrays.copyOfRange(tokens, 0, j), i + 1, where, errors);
                    if (map == null) {
                        b.broken = true;
                        return b;
                    }
                    b.transforms.add(new Recolor(lineNo, map));
                    i = j;
                }
                default -> {
                    errors.add(where + "unknown option '" + tokens[i]
                            + "'; expected flipx, flipy, shift <dx> <dy> or recolor <from>><to> ...");
                    b.broken = true;
                    return b;
                }
            }
        }
        return b;
    }

    /** {@code anim <name> fps <n> loop|once: <frame>[*<duration>] ...}; frame names are checked later. */
    private static Anim parseAnim(String[] tokens, String where, List<String> errors) {
        String form = "anim <name> fps <n> loop|once: <frame>[*<duration>] ...";
        if (tokens.length < 5 || !NAME.matcher(tokens[1]).matches() || !tokens[2].equals("fps")) {
            errors.add(where + "expected '" + form + "'");
            return null;
        }
        double fps = number(tokens[3]);
        if (!(fps > 0 && fps <= MAX_FPS)) {
            errors.add(where + "fps must be a number above 0 and at most " + MAX_FPS + ", not '" + tokens[3] + "'");
            return null;
        }
        String mode = tokens[4];
        int i = 5;
        if (mode.endsWith(":")) {
            mode = mode.substring(0, mode.length() - 1);
        } else if (i < tokens.length && tokens[i].equals(":")) {
            i++;
        }
        if (!mode.equals("loop") && !mode.equals("once")) {
            errors.add(where + "expected 'loop:' or 'once:' after the fps, not '" + tokens[4] + "'");
            return null;
        }
        List<Anim.Step> steps = new ArrayList<>();
        for (; i < tokens.length; i++) {
            String t = tokens[i];
            int star = t.indexOf('*');
            String frame = star < 0 ? t : t.substring(0, star);
            double duration = star < 0 ? 1 : number(t.substring(star + 1));
            if (!NAME.matcher(frame).matches()) {
                errors.add(where + "'" + t + "' is not a frame name; expected <frame> or <frame>*<duration>, like down_0*2");
                return null;
            }
            if (!(duration > 0 && duration <= MAX_FPS)) {
                errors.add(where + "'" + t + "': the duration after '*' must be a number above 0, like down_0*2");
                return null;
            }
            steps.add(new Anim.Step(frame, duration));
        }
        if (steps.isEmpty()) {
            errors.add(where + "anim '" + tokens[1] + "' has no frames; list them after '" + mode + ":'");
            return null;
        }
        return new Anim(tokens[1], fps, mode.equals("loop"), steps);
    }

    /** A plain decimal number such as 8 or 1.5, or NaN. */
    private static double number(String text) {
        if (!text.matches("\\d+(\\.\\d+)?")) return Double.NaN;
        return Double.parseDouble(text);
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
            case "blend" -> parseBlend(tokens, lineNo, where, errors);
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

    /** {@code blend <terrain> <profile>|<profile>...} or with {@code &}; one kind of operator per line. */
    private static Blend parseBlend(String[] tokens, int lineNo, String where, List<String> errors) {
        String form = "blend <terrain> <profile>[|<profile>...] or <profile>&<profile>...";
        if (tokens.length != 3 || !NAME.matcher(tokens[1]).matches()) {
            errors.add(where + "expected '" + form + "', like 'blend grass_dirt n|w'");
            return null;
        }
        String expr = tokens[2];
        boolean union = expr.contains("|"), all = expr.contains("&");
        if (union && all) {
            errors.add(where + "'" + expr + "' mixes | and &; use | for an outer corner or & for an inner one");
            return null;
        }
        List<String> names = List.of(expr.split(union ? "\\|" : "&", -1));
        for (String n : names) {
            if (!PROFILE.matcher(n).matches()) {
                errors.add(where + "'" + n + "' isn't a profile name (they start with n, s, w or e)");
                return null;
            }
        }
        return new Blend(lineNo, tokens[1], names, union || !all);
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
                                   Set<String> declaredTerrains, Map<String, Terrain> terrains,
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
                case Blend bl -> {
                    if (!b.rows.isEmpty() || b.ops.getFirst() != bl) {
                        errors.add(where + b.label() + ": 'blend' makes the whole frame, so it comes first, in a "
                                + "frame without grid rows; touch it up with lines after it");
                        grid = null;
                        continue;
                    }
                    Terrain t = terrains.get(bl.terrain());
                    if (t == null) {
                        if (!declaredTerrains.contains(bl.terrain())) {  // a declared terrain with errors is already reported
                            errors.add(where + b.label() + ": unknown terrain '" + bl.terrain() + "'"
                                    + (declaredTerrains.isEmpty() ? " (no terrains are defined)"
                                    : " (terrains: " + String.join(", ", declaredTerrains) + ")"));
                        }
                        grid = null;
                        continue;
                    }
                    List<String> unknown = bl.profiles().stream().filter(p -> !t.profiles().containsKey(p)).toList();
                    if (!unknown.isEmpty()) {
                        errors.add(where + b.label() + ": terrain '" + t.name() + "' has no profile "
                                + String.join(", ", unknown) + " (profiles: "
                                + String.join(", ", t.profiles().keySet().stream().sorted().toList()) + ")");
                        grid = null;
                        continue;
                    }
                    grid = t.blend(bl.profiles(), bl.union());
                }
            }
        }
        if (b.broken || errors.size() > before) return null;
        return grid;
    }

    /** Reads one line inside a {@code terrain} block. */
    private static void readTerrainLine(TerrainDef def, String[] tokens, int lineNo, String where,
                                        List<String> errors) {
        String key = tokens[0];
        switch (key) {
            case "raised", "lower", "bank", "lip", "shadow" -> {
                boolean symbol = !key.equals("raised") && !key.equals("lower");
                if (tokens.length != 2 || (symbol ? !isSymbol(tokens[1]) : !NAME.matcher(tokens[1]).matches())) {
                    errors.add(where + "expected '" + key + (symbol ? " <colour>'" : " <part>'"));
                    return;
                }
                Integer first = def.settingLines.putIfAbsent(key, lineNo);
                if (first != null) {
                    errors.add(where + "'" + key + "' is already set on line " + first);
                    return;
                }
                def.settings.put(key, new String[] {tokens[1], String.valueOf(lineNo)});
            }
            default -> {
                if (!PROFILE.matcher(key).matches() || tokens.length < 2) {
                    errors.add(where + "unknown line in terrain '" + def.name + "'; expected raised, lower, bank, lip,"
                            + " shadow, or a profile like 'n 5 5 6 6 ...' (a name starting with n, s, w or e)");
                    return;
                }
                Integer first = def.profileLines.putIfAbsent(key, lineNo);
                if (first != null) {
                    errors.add(where + "profile '" + key + "' is already defined on line " + first);
                    return;
                }
                def.profiles.put(key, java.util.Arrays.copyOfRange(tokens, 1, tokens.length));
            }
        }
    }

    /** Checks a terrain against the parts and palette; null after an error. */
    private static Terrain checkTerrain(TerrainDef def, String source, int width, int height, Palette palette,
                                        Set<String> declaredParts, Map<String, Grid> parts, List<String> errors,
                                        List<String> warnings) {
        int before = errors.size();
        String where = source + ":" + def.line + ": terrain '" + def.name + "': ";
        Grid[] textures = new Grid[2];
        String[] keys = {"raised", "lower"};
        for (int i = 0; i < 2; i++) {
            String[] s = def.settings.get(keys[i]);
            if (s == null) {
                errors.add(where + "missing '" + keys[i] + " <part>'");
                continue;
            }
            String w = source + ":" + s[1] + ": ";
            Grid g = parts.get(s[0]);
            if (g == null) {
                if (!declaredParts.contains(s[0])) {
                    errors.add(w + "unknown part '" + s[0] + "'" + (declaredParts.isEmpty() ? " (no parts are defined)"
                            : " (parts: " + String.join(", ", declaredParts) + ")"));
                } else {
                    errors.add(w + "part '" + s[0] + "' has errors");
                }
            } else if (width > 0 && (g.width() != width || g.height() != height)) {
                errors.add(w + "part '" + s[0] + "' is " + g.width() + "x" + g.height() + "; a terrain's textures are "
                        + "full frames, " + width + "x" + height);
            } else {
                textures[i] = g;
            }
        }
        char[] symbols = new char[3];
        String[] symbolKeys = {"bank", "lip", "shadow"};
        for (int i = 0; i < 3; i++) {
            String[] s = def.settings.get(symbolKeys[i]);
            if (s == null) continue;
            if (checkSymbol(s[0].charAt(0), palette, source + ":" + s[1] + ": ", errors)) symbols[i] = s[0].charAt(0);
        }
        if (def.profiles.isEmpty()) errors.add(where + "no profiles; add lines like 'n 5 5 6 6 ...'");
        Map<String, int[]> profiles = new LinkedHashMap<>();
        Map<Character, String> firstOfSide = new HashMap<>();
        for (Map.Entry<String, String[]> e : def.profiles.entrySet()) {
            String name = e.getKey();
            String w = source + ":" + def.profileLines.get(name) + ": profile '" + name + "': ";
            char side = name.charAt(0);
            int length = side == 'n' || side == 's' ? width : height;
            int across = side == 'n' || side == 's' ? height : width;
            String[] raw = e.getValue();
            if (length > 0 && raw.length != length) {
                errors.add(w + "has " + raw.length + " values; it needs one per " + (side == 'n' || side == 's'
                        ? "column (" + length + ")" : "row (" + length + ")"));
                continue;
            }
            int[] d = new int[raw.length];
            boolean ok = true;
            for (int i = 0; i < raw.length && ok; i++) {
                if (!raw[i].matches("\\d{1,3}") || (across > 0 && Integer.parseInt(raw[i]) > across)) {
                    errors.add(w + "'" + raw[i] + "' isn't a depth from 0 to " + across);
                    ok = false;
                } else {
                    d[i] = Integer.parseInt(raw[i]);
                }
            }
            if (!ok) continue;
            if (d[0] != d[d.length - 1]) {
                warnings.add(w + "starts at " + d[0] + " and ends at " + d[d.length - 1]
                        + "; a tile using it won't fit next to a copy of itself");
            }
            String other = firstOfSide.putIfAbsent(side, name);
            if (other != null && (profiles.get(other)[0] != d[0] || profiles.get(other)[d.length - 1] != d[d.length - 1])) {
                warnings.add(w + "ends at different depths from '" + other + "', so their tiles won't meet");
            }
            profiles.put(name, d);
        }
        if (errors.size() > before || textures[0] == null || textures[1] == null) return null;
        return new Terrain(def.name, textures[0], textures[1], symbols[0], symbols[1], symbols[2], profiles);
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
    static List<String> sortByLine(List<String> messages, String source) {
        String prefix = source + ":";
        return messages.stream().sorted(Comparator.comparingInt(m -> {
            if (!m.startsWith(prefix)) return 0;
            int i = prefix.length(), j = i;
            while (j < m.length() && Character.isDigit(m.charAt(j))) j++;
            return j > i ? Integer.parseInt(m.substring(i, j)) : 0;
        })).toList();
    }

    static Palette loadPalette(String nameOrPath, String where, PaletteResolver palettes,
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
