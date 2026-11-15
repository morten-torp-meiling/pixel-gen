package pixelgen.cli;

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

    /**
     * Expands files and folders (searched recursively for {@code .sprite}) into a sorted list.
     * Throws NoSuchFileException for a target that doesn't exist.
     */
    static List<Path> collect(Path projectDir, List<String> targets) throws IOException {
        List<Path> files = new ArrayList<>();
        for (String t : targets) {
            Path p = projectDir.resolve(t);
            if (Files.isDirectory(p)) {
                try (Stream<Path> s = Files.walk(p)) {
                    s.filter(f -> f.toString().endsWith(".sprite")).sorted().forEach(files::add);
                }
            } else if (Files.isRegularFile(p)) {
                files.add(p);
            } else {
                throw new NoSuchFileException(t, null, "not found");
            }
        }
        return files;
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
