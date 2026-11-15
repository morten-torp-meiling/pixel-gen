package pixelgen.cli;

import pixelgen.model.Sprite;
import pixelgen.parse.PaletteResolver;
import pixelgen.parse.ParseException;
import pixelgen.parse.SpriteParser;
import pixelgen.parse.TilesetParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** Shared helpers for commands that take sprite files or folders. */
final class SpriteFiles {

    private SpriteFiles() {}

    /** Both source kinds: sprites and tilesets (whose tiles load as frames). */
    static final List<String> ALL = List.of(".sprite", ".tileset");
    static final List<String> SPRITES = List.of(".sprite");
    static final List<String> TILESETS = List.of(".tileset");

    /** Sprites and tilesets; see {@link #collect(Path, List, List)}. */
    static List<Path> collect(Path projectDir, List<String> targets) throws IOException {
        return collect(projectDir, targets, ALL);
    }

    /**
     * Expands files and folders (searched recursively for files with one of {@code extensions})
     * into a sorted list. A file named directly is taken whatever its extension. Throws
     * NoSuchFileException for a target that doesn't exist.
     */
    static List<Path> collect(Path projectDir, List<String> targets, List<String> extensions) throws IOException {
        List<Path> files = new ArrayList<>();
        for (String t : targets) {
            Path p = projectDir.resolve(t);
            if (Files.isDirectory(p)) {
                try (Stream<Path> s = Files.walk(p)) {
                    s.filter(f -> extensions.stream().anyMatch(e -> f.toString().endsWith(e))).sorted().forEach(files::add);
                }
            } else if (Files.isRegularFile(p)) {
                files.add(p);
            } else {
                throw new NoSuchFileException(t, null, "not found");
            }
        }
        return files;
    }

    /** "no .sprite or .tileset files found in assets". */
    static String noneFound(List<String> extensions, List<String> targets) {
        return "no " + String.join(" or ", extensions) + " files found in " + String.join(", ", targets);
    }

    /** Loads a sprite, or a tileset as a sprite whose frames are its tiles. */
    static Sprite load(Path file, String shown, PaletteResolver palettes) throws IOException, ParseException {
        return file.toString().endsWith(".tileset") ? TilesetParser.parseFile(file, shown, palettes).sprite()
                : SpriteParser.parseFile(file, shown, palettes);
    }

    /** Project-relative path with forward slashes, so messages are short and stable. */
    static String display(Path projectDir, Path file) {
        Path abs = file.toAbsolutePath().normalize();
        Path base = projectDir.toAbsolutePath().normalize();
        return (abs.startsWith(base) ? base.relativize(abs) : abs).toString().replace('\\', '/');
    }

    /** Prints a parse error message, indenting the continuation lines of multi-line messages. */
    static void printError(java.io.PrintStream out, String error) {
        out.println("     " + error.replace("\n", "\n     "));
    }
}
