package pixelgen.parse;

import pixelgen.model.Style;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

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

    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_-]+");

    private Styles() {}

    /** Resolves a name or path against {@code projectDir}. */
    public static Path resolve(String nameOrPath, Path projectDir) {
        if (nameOrPath.endsWith(StyleParser.EXTENSION) || nameOrPath.contains("/") || nameOrPath.contains("\\")) {
            return projectDir.resolve(nameOrPath);
        }
        return projectDir.resolve(FOLDER).resolve(nameOrPath + StyleParser.EXTENSION);
    }

    /** Loads a style; its palette comes from {@code palettes}. */
    public static Style load(String nameOrPath, Path projectDir, PaletteResolver palettes)
            throws IOException, ParseException {
        Path file = resolve(nameOrPath, projectDir);
        if (!Files.isRegularFile(file)) {
            throw new NoSuchFileException(file.toString(), null, "style not found");
        }
        return StyleParser.parseFile(file, display(file, projectDir), palettes);
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
            } else if (!NAME.matcher(value).matches()) {
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

    /** {@code styles/cave.style} for a file inside the project, else the full path. */
    static String display(Path file, Path projectDir) {
        Path abs = file.toAbsolutePath().normalize(), dir = projectDir.toAbsolutePath().normalize();
        return abs.startsWith(dir) ? dir.relativize(abs).toString().replace('\\', '/') : abs.toString();
    }
}
