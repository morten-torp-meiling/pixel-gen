package pixelgen.parse;

import pixelgen.model.Effects;
import pixelgen.model.Light;
import pixelgen.model.Palette;
import pixelgen.model.Style;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses {@code .style} files.
 *
 * <pre>
 * # comment (a line whose first non-space character is #)
 * style cave             optional; defaults to the file name
 * palette cave           required: the palette its files use unless they name another
 * light top-right        optional: top-left (default), top-right, bottom-left or bottom-right
 * outline K selective    optional: what a bare 'outline' line draws
 * shadow ~               optional: the colour of a 'shadow &lt;w&gt; &lt;h&gt;' line
 * tile 16 16             optional: tile size for tilesets with no 'size' line (default 16 16)
 * guide cave.md          optional: the style's rules, next to the .style file (default &lt;name&gt;.md)
 * </pre>
 *
 * Every line may appear once. A missing guide is a warning, not an error.
 */
public final class StyleParser {

    public static final String EXTENSION = ".style";

    private static final List<String> KEYS = List.of("style", "palette", "light", "outline", "shadow", "tile", "guide");
    private static final String KNOWN = String.join(", ", KEYS);

    private StyleParser() {}

    /**
     * @param displayName how messages name the file, such as {@code styles/cave.style}
     */
    public static Style parseFile(Path file, String displayName, PaletteResolver palettes)
            throws IOException, ParseException {
        String defaultName = ProjectFiles.baseName(file, EXTENSION);
        return parse(Files.readString(file, StandardCharsets.UTF_8), displayName, defaultName,
                file.toAbsolutePath().getParent(), palettes);
    }

    /**
     * @param dir the folder the guide is looked for in (the style file's folder)
     */
    public static Style parse(String text, String source, String defaultName, Path dir, PaletteResolver palettes)
            throws ParseException {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Map<String, Integer> seen = new HashMap<>();

        String name = defaultName;
        String paletteName = null;
        Light light = Light.TOP_LEFT;
        char outline = 0, shadow = 0;
        Effects.OutlineMode outlineMode = Effects.OutlineMode.OUTER;
        int tileWidth = Style.DEFAULT_TILE_SIZE, tileHeight = Style.DEFAULT_TILE_SIZE;
        String guide = null;
        int paletteLine = 0, outlineLine = 0, shadowLine = 0, guideLine = 0;

        String[] lines = text.split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            int lineNo = i + 1;
            String line = lines[i].strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String where = source + ":" + lineNo + ": ";
            String[] tokens = line.split("\\s+");
            String key = tokens[0];
            if (KEYS.contains(key)) {
                Integer first = seen.putIfAbsent(key, lineNo);
                if (first != null) {
                    errors.add(where + "'" + key + "' is already set on line " + first);
                    continue;
                }
            }
            switch (key) {
                case "style" -> {
                    if (tokens.length != 2 || !SpriteParser.isName(tokens[1])) {
                        errors.add(where + "expected 'style <name>' (letters, digits, _ and -)");
                    } else {
                        name = tokens[1];
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
                case "light" -> {
                    Light l = tokens.length == 2 ? Light.parse(tokens[1]) : null;
                    if (l == null) errors.add(where + "expected 'light <direction>', with " + Light.choices());
                    else light = l;
                }
                case "outline" -> {
                    if (tokens.length < 2 || tokens.length > 3 || !SpriteParser.isSymbol(tokens[1])) {
                        errors.add(where + "expected 'outline <colour> [outer|selective]', like 'outline K selective'");
                        continue;
                    }
                    Effects.OutlineMode mode = tokens.length == 3
                            ? SpriteParser.outlineMode(tokens[2], where, errors) : Effects.OutlineMode.OUTER;
                    if (mode == null) continue;
                    outlineMode = mode;
                    outline = tokens[1].charAt(0);
                    outlineLine = lineNo;
                }
                case "shadow" -> {
                    if (tokens.length == 4 && SpriteParser.isSymbol(tokens[1])) {
                        errors.add(where + "a style sets only the shadow's colour ('shadow " + tokens[1]
                                + "'); each sprite gives its size with 'shadow <width> <height>'");
                    } else if (tokens.length != 2 || !SpriteParser.isSymbol(tokens[1])) {
                        errors.add(where + "expected 'shadow <colour>', like 'shadow ~'");
                    } else {
                        shadow = tokens[1].charAt(0);
                        shadowLine = lineNo;
                    }
                }
                case "tile" -> {
                    int[] wh = SpriteParser.ints(tokens, 1, 2, where, "tile <width> <height>", errors);
                    if (wh == null) continue;
                    if (wh[0] < 1 || wh[1] < 1 || wh[0] > SpriteParser.MAX_SIZE || wh[1] > SpriteParser.MAX_SIZE) {
                        errors.add(where + "tile size must be between 1 and " + SpriteParser.MAX_SIZE);
                    } else {
                        tileWidth = wh[0];
                        tileHeight = wh[1];
                    }
                }
                case "guide" -> {
                    if (tokens.length != 2) {
                        errors.add(where + "expected 'guide <file.md>', a file next to the style");
                    } else {
                        guide = tokens[1];
                        guideLine = lineNo;
                    }
                }
                default -> errors.add(where + "unknown line '" + line + "'; a style has these lines: " + KNOWN);
            }
        }

        Palette palette = null;
        if (paletteName == null) {
            if (!seen.containsKey("palette")) errors.add(source + ": missing 'palette <name>'");
        } else {
            palette = SpriteParser.loadPalette(paletteName, source + ":" + paletteLine + ": ", palettes, errors);
        }
        if (palette != null) {
            if (outline != 0 && !palette.contains(outline)) {
                errors.add(source + ":" + outlineLine + ": '" + outline + "' is not in palette '" + palette.name() + "'");
            }
            if (shadow != 0 && !palette.contains(shadow)) {
                errors.add(source + ":" + shadowLine + ": '" + shadow + "' is not in palette '" + palette.name() + "'");
            }
        }

        Path guidePath = dir.resolve(guide != null ? guide : name + ".md").normalize();
        if (!Files.isRegularFile(guidePath)) {
            warnings.add(guide != null
                    ? source + ":" + guideLine + ": guide '" + guide + "' not found (" + guidePath + ")"
                    : source + ": no guide " + name + ".md next to the style; write the style's rules there, "
                            + "or name another file with 'guide <file.md>'");
        }

        if (!errors.isEmpty()) throw new ParseException(SpriteParser.sortByLine(errors, source));
        return new Style(name, palette, light, outline, outlineMode, shadow, tileWidth, tileHeight, guidePath,
                SpriteParser.sortByLine(warnings, source));
    }
}
