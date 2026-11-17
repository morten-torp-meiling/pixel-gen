package pixelgen.cli;

import pixelgen.model.Sprite;
import pixelgen.parse.PaletteResolver;
import pixelgen.parse.ParseException;
import pixelgen.parse.ProjectFiles;
import pixelgen.parse.SpriteParser;
import pixelgen.parse.TilesetParser;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** Shared helpers for commands that take sprite files or folders. */
final class SpriteFiles {

    private SpriteFiles() {}

    /** Both source kinds: sprites and tilesets (whose tiles load as frames). */
    static final List<String> ALL = List.of(".sprite", ".tileset");
    static final List<String> SPRITES = List.of(".sprite");
    static final List<String> TILESETS = List.of(".tileset");

    /** Reads one file into what a command works on. */
    @FunctionalInterface
    interface Loader<T> {
        T load(Path file, String shown, PaletteResolver palettes) throws IOException, ParseException;
    }

    /** A command's work on one loaded file; returns false when the file failed (and says why). */
    @FunctionalInterface
    interface Job<T> {
        boolean run(T loaded, String shown) throws IOException;
    }

    /** How many files a run looked at, and how many failed. */
    record Tally(int files, int failed) {
        int exitCode() { return failed == 0 ? 0 : 1; }
    }

    /**
     * Loads every file the targets name (see {@link #collect}) and runs {@code job} on each, going
     * on after a failure. A file that doesn't parse is reported as FAIL with its errors; a job that
     * throws IllegalArgumentException (a sheet or image too large) fails that file with its message.
     * A target that doesn't exist, or targets with no files, are reported on {@code err} as one failure.
     */
    static <T> Tally forEach(Path projectDir, List<String> targets, List<String> extensions, Loader<T> loader,
                             PrintStream out, PrintStream err, Job<T> job) throws IOException {
        List<Path> files;
        try {
            files = collect(projectDir, targets, extensions);
        } catch (NoSuchFileException e) {
            err.println("not found: " + e.getFile());
            return new Tally(0, 1);
        }
        if (files.isEmpty()) {
            err.println("no " + String.join(" or ", extensions) + " files found in " + String.join(", ", targets));
            return new Tally(0, 1);
        }
        PaletteResolver palettes = PaletteResolver.cached(projectDir);
        int failed = 0;
        for (Path f : files) {
            String shown = ProjectFiles.display(projectDir, f);
            try {
                if (!job.run(loader.load(f, shown, palettes), shown)) failed++;
            } catch (ParseException e) {
                failed++;
                out.println("FAIL " + shown);
                for (String error : e.errors()) printError(out, error);
            } catch (IllegalArgumentException e) {
                failed++;
                out.println("FAIL " + shown + ": " + e.getMessage());
            }
        }
        return new Tally(files.size(), failed);
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

    /** Loads a sprite, or a tileset as a sprite whose frames are its tiles. */
    static Sprite load(Path file, String shown, PaletteResolver palettes) throws IOException, ParseException {
        return file.toString().endsWith(".tileset") ? TilesetParser.parseFile(file, shown, palettes).sprite()
                : SpriteParser.parseFile(file, shown, palettes);
    }

    /**
     * Notices two files of one run writing their images under the same name (the same sprite name
     * in two folders, or a variant named like another sprite): the later one replaces the earlier.
     */
    static final class Names {
        private final Map<String, String> seen = new HashMap<>();

        /** Prints a warning when another file of this run already wrote images as {@code name}. */
        void check(String name, String shown, PrintStream out) {
            String other = seen.putIfAbsent(name, shown);
            if (other != null && !other.equals(shown)) {
                out.println("     warning: " + other + " also writes images as '" + name + "'; this file replaces them");
            }
        }
    }

    /** Prints a parse error message, indenting the continuation lines of multi-line messages. */
    static void printError(PrintStream out, String error) {
        out.println("     " + error.replace("\n", "\n     "));
    }

    /** Prints each warning under the file's line. */
    static void printWarnings(PrintStream out, List<String> warnings) {
        for (String w : warnings) out.println("     warning: " + w);
    }
}
