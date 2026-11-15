package pixelgen.cli;

import pixelgen.Cli;
import pixelgen.model.Sprite;
import pixelgen.parse.PaletteResolver;
import pixelgen.parse.ParseException;
import pixelgen.parse.SpriteParser;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * {@code validate [file|folder ...]} checks sprite files and reports every error with its line.
 * Folders are searched for {@code .sprite} files; with no arguments, {@code assets/} is checked.
 * Exit code 0 when all files are valid, 1 when any has errors.
 */
public final class ValidateCommand implements Cli.Command {

    private final Path projectDir;

    public ValidateCommand(Path projectDir) { this.projectDir = projectDir; }

    public String usage() { return "validate [file|folder ...]"; }

    public String summary() { return "Check .sprite files (default: everything under assets/)"; }

    public int run(List<String> args, PrintStream out, PrintStream err) throws IOException {
        List<String> targets = args.isEmpty() ? List.of("assets") : args;
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
                err.println("not found: " + t);
                return 1;
            }
        }
        if (files.isEmpty()) {
            err.println("no .sprite files found in " + String.join(", ", targets));
            return 1;
        }

        PaletteResolver palettes = PaletteResolver.cached(projectDir);
        int failed = 0;
        for (Path f : files) {
            String shown = display(f);
            try {
                Sprite s = SpriteParser.parseFile(f, shown, palettes);
                out.printf("ok   %s (%dx%d, %d frames, %d parts, palette %s)%n", shown, s.width(), s.height(),
                        s.frames().size(), s.parts().size(), s.palette().name());
                for (String w : s.warnings()) out.println("     warning: " + w);
            } catch (ParseException e) {
                failed++;
                out.println("FAIL " + shown);
                for (String error : e.errors()) out.println("     " + error.replace("\n", "\n     "));
            }
        }
        out.printf("%d file(s) checked, %d with errors%n", files.size(), failed);
        return failed == 0 ? 0 : 1;
    }

    /** Project-relative path with forward slashes, so messages are short and stable. */
    private String display(Path f) {
        Path abs = f.toAbsolutePath().normalize();
        Path base = projectDir.toAbsolutePath().normalize();
        return (abs.startsWith(base) ? base.relativize(abs) : abs).toString().replace('\\', '/');
    }
}
