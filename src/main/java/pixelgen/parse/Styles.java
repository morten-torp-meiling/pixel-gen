package pixelgen.parse;

import pixelgen.model.Style;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Finds styles by name ({@code cave} → {@code styles/cave.style}) or by file path, and the
 * project's default style, named in {@code styles/styles.conf}:
 *
 * <pre>
 * # The style of files without a 'style' line.
 * default = farm
 * </pre>
 */
public final class Styles {

    public static final String FOLDER = "styles";
    public static final String CONF = FOLDER + "/styles.conf";

    private Styles() {}

    /** Resolves a name or path against {@code projectDir}. */
    public static Path resolve(String nameOrPath, Path projectDir) {
        return ProjectFiles.resolve(nameOrPath, projectDir, FOLDER, StyleParser.EXTENSION);
    }

    /** Loads a style; its palette comes from {@code palettes}. */
    public static Style load(String nameOrPath, Path projectDir, PaletteResolver palettes)
            throws IOException, ParseException {
        Path file = resolve(nameOrPath, projectDir);
        if (!Files.isRegularFile(file)) {
            throw new NoSuchFileException(file.toString(), null, "style not found");
        }
        return StyleParser.parseFile(file, ProjectFiles.display(projectDir, file), palettes);
    }

    /**
     * The default style's name from {@code styles/styles.conf}, or null when there is no such
     * file or it sets no default (then files without a 'style' line get {@link Style#BUILT_IN}).
     */
    public static String defaultName(Path projectDir) throws IOException, ParseException {
        Path file = projectDir.resolve(CONF);
        if (!Files.isRegularFile(file)) return null;
        List<String> errors = new ArrayList<>();
        String[] name = {null};
        KeyValues.read(Files.readString(file, StandardCharsets.UTF_8), CONF, errors, (key, value, line, where) -> {
            if (!key.equals("default")) {
                errors.add(where + "unknown setting '" + key + "'; known: default");
            } else if (!SpriteParser.isName(value)) {
                errors.add(where + "expected 'default = <style name>', like 'default = farm'");
            } else {
                name[0] = value;
            }
        });
        if (!errors.isEmpty()) throw new ParseException(errors);
        return name[0];
    }

    /** The project's style files, sorted by name. */
    public static List<Path> list(Path projectDir) throws IOException {
        Path dir = projectDir.resolve(FOLDER);
        if (!Files.isDirectory(dir)) return List.of();
        try (var files = Files.list(dir)) {
            return files.filter(f -> f.getFileName().toString().endsWith(StyleParser.EXTENSION)
                            && Files.isRegularFile(f))
                    .sorted().toList();
        }
    }
}
