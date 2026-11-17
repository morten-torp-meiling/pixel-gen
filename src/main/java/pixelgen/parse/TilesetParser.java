package pixelgen.parse;

import pixelgen.model.Sprite;
import pixelgen.model.Tileset;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses {@code .tileset} files: the sprite format with tiles in place of frames.
 *
 * <pre>
 * tileset ground          optional; defaults to the file name
 * size 16 16              optional; tiles take the style's tile size (16x16 by default) unless set
 * style farm              optional, as in sprites
 * palette farm            required unless the style names one
 * columns 4               optional: tiles per row in the atlas (default: up to 8)
 *
 * part tuft ... end       parts, stamps, recolor, outline and variants work as in sprites
 *
 * tile grass_a            a tile that must repeat seamlessly both ways
 *   ...16 rows...
 * end
 * tile path_n wrap x      wrap both|x|y|none: which edges must meet when the tile repeats
 *   ...
 * end
 * tile grass_b from grass_a flipx       derived tiles, as derived frames in sprites
 *
 * variant ground_winter recolor g>s G>S
 * </pre>
 *
 * The file is translated line by line into the sprite format (keeping line numbers) and parsed
 * by {@link SpriteParser}; its messages are translated back, so they speak of tiles.
 */
public final class TilesetParser {

    public static final int MAX_COLUMNS = 256;

    private static final Set<String> KEYWORDS = Set.of("tileset", "size", "palette", "columns", "part", "tile",
            "variant", "terrain", "end", "sprite", "frame", "anim", "anchor");

    private TilesetParser() {}

    public static Tileset parseFile(Path file, String displayName, PaletteResolver palettes)
            throws IOException, ParseException {
        String defaultName = ProjectFiles.baseName(file, ".tileset");
        return parse(Files.readString(file, StandardCharsets.UTF_8), displayName, defaultName, palettes);
    }

    public static Tileset parse(String text, String source, String defaultName, PaletteResolver palettes)
            throws ParseException {
        List<String> errors = new ArrayList<>();
        Map<String, Tileset.Wrap> wraps = new LinkedHashMap<>();
        int columns = 0, columnsLine = 0;

        String[] lines = text.split("\\R", -1);
        StringBuilder sprite = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            int lineNo = i + 1;
            String line = lines[i].strip();
            String[] tokens = line.split("\\s+");
            String where = source + ":" + lineNo + ": ";
            // Grid rows and frame operations pass through; only directive lines are translated.
            boolean directive = !line.startsWith("#") && KEYWORDS.contains(tokens[0])
                    && (tokens.length > 1 || line.equals("end"));
            String out = lines[i];
            if (directive) {
                switch (tokens[0]) {
                    case "tileset" -> out = "sprite" + line.substring("tileset".length());
                    case "columns" -> {
                        out = "";
                        if (columnsLine != 0) {
                            errors.add(where + "'columns' is already set on line " + columnsLine);
                        } else if (tokens.length != 2 || !tokens[1].matches("\\d{1,4}")
                                || Integer.parseInt(tokens[1]) < 1 || Integer.parseInt(tokens[1]) > MAX_COLUMNS) {
                            errors.add(where + "expected 'columns <n>' with n from 1 to " + MAX_COLUMNS);
                            columnsLine = lineNo;
                        } else {
                            columns = Integer.parseInt(tokens[1]);
                            columnsLine = lineNo;
                        }
                    }
                    case "tile" -> out = tileLine(tokens, where, wraps, errors);
                    case "sprite", "frame" -> {  // kept, so the block that follows still parses
                        errors.add(where + "a tileset uses '" + (tokens[0].equals("sprite") ? "tileset" : "tile")
                                + "' where a sprite uses '" + tokens[0] + "'");
                    }
                    case "anim" -> {
                        out = "";
                        errors.add(where + "tiles can't be animated (yet); draw each look as its own tile");
                    }
                    case "anchor" -> {
                        out = "";
                        errors.add(where + "tiles have no anchor; they sit on the tile grid");
                    }
                    default -> { }
                }
            }
            sprite.append(out).append('\n');
        }

        Sprite s = null;
        try {
            s = SpriteParser.parse(sprite.toString(), source, defaultName, palettes, true);
        } catch (ParseException e) {
            for (String m : e.errors()) errors.add(translate(m, source));
        }
        if (!errors.isEmpty()) throw new ParseException(SpriteParser.sortByLine(errors, source));

        List<String> warnings = s.warnings().stream().map(w -> translate(w, source)).toList();
        Sprite tiles = new Sprite(s.name(), s.width(), s.height(), s.palette(), s.style(), s.anchorX(), s.anchorY(),
                s.parts(), s.frames(), s.anims(), s.variants(), warnings);
        int cols = columns > 0 ? columns : Math.min(tiles.frames().size(), Tileset.DEFAULT_COLUMNS);
        return new Tileset(tiles, cols, wraps);
    }

    /** {@code tile <name> [from ...] [wrap <how>]} becomes {@code frame <name> [from ...]}. */
    private static String tileLine(String[] tokens, String where, Map<String, Tileset.Wrap> wraps,
                                   List<String> errors) {
        List<String> kept = new ArrayList<>();
        kept.add("frame");
        for (int i = 1; i < tokens.length; i++) {
            if (!tokens[i].equals("wrap")) {
                kept.add(tokens[i]);
                continue;
            }
            String how = i + 1 < tokens.length ? tokens[++i] : "";
            Tileset.Wrap wrap = switch (how) {
                case "both" -> Tileset.Wrap.BOTH;
                case "x" -> Tileset.Wrap.X;
                case "y" -> Tileset.Wrap.Y;
                case "none" -> Tileset.Wrap.NONE;
                default -> null;
            };
            if (wrap == null) {
                errors.add(where + "expected 'wrap both', 'wrap x', 'wrap y' or 'wrap none'"
                        + (how.isEmpty() ? "" : ", not 'wrap " + how + "'"));
            } else if (tokens.length > 1) {
                wraps.put(tokens[1], wrap);
            }
        }
        return String.join(" ", kept);
    }

    /** A name in quotes, or a path (anything with a slash). */
    private static final Pattern KEPT = Pattern.compile("'([A-Za-z0-9_-]+)'|[^\\s()]*[/\\\\][^\\s()]*");
    private static final Set<String> WORDS = Set.of("frame", "frames", "sprite", "sprites");

    /**
     * A sprite-parser message in tileset words: frame becomes tile, sprite becomes tileset. The
     * {@code source:line:} prefix, paths, and names in quotes ({@code 'grass_a'}) are left alone;
     * keywords in quotes ({@code 'frame <name>'}) are not. Lines after the first quote another
     * file's errors (a palette's or a style's) and are left alone too.
     */
    static String translate(String message, String source) {
        int start = 0;
        String prefix = source + ":";
        if (message.startsWith(prefix)) {
            int i = prefix.length();
            while (i < message.length() && Character.isDigit(message.charAt(i))) i++;
            if (i > prefix.length() && message.startsWith(":", i)) i++;
            start = i;
        }
        int end = message.indexOf('\n');
        if (end < 0) end = message.length();
        return message.substring(0, start) + words(message.substring(start, end)) + message.substring(end);
    }

    private static String words(String message) {
        List<String> names = new ArrayList<>();
        Matcher m = KEPT.matcher(message);
        StringBuilder protectedText = new StringBuilder();
        while (m.find()) {
            if (m.group(1) != null && WORDS.contains(m.group(1))) continue;
            m.appendReplacement(protectedText, "\u0000" + names.size() + "\u0000");
            names.add(m.group());
        }
        m.appendTail(protectedText);
        String t = protectedText.toString()
                .replaceAll("\\bframes\\b", "tiles")
                .replaceAll("\\bframe\\b", "tile")
                .replaceAll("\\bsprites\\b", "tilesets")
                .replaceAll("\\bsprite\\b", "tileset");
        for (int i = 0; i < names.size(); i++) t = t.replace("\u0000" + i + "\u0000", names.get(i));
        return t;
    }
}
