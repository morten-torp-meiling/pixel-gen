package pixelgen.cli;

import pixelgen.Cli;
import pixelgen.model.Sprite;
import pixelgen.parse.PaletteResolver;
import pixelgen.parse.ParseException;
import pixelgen.parse.SpriteParser;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;

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
        List<Path> files;
        try {
            files = SpriteFiles.collect(projectDir, targets);
        } catch (NoSuchFileException e) {
            err.println("not found: " + e.getFile());
            return 1;
        }
        if (files.isEmpty()) {
            err.println("no .sprite files found in " + String.join(", ", targets));
            return 1;
        }

        PaletteResolver palettes = PaletteResolver.cached(projectDir);
        int failed = 0;
        for (Path f : files) {
            String shown = SpriteFiles.display(projectDir, f);
            try {
                Sprite s = SpriteParser.parseFile(f, shown, palettes);
                out.printf("ok   %s (%dx%d, %d frames, %d parts, palette %s)%n", shown, s.width(), s.height(),
                        s.frames().size(), s.parts().size(), s.palette().name());
                for (String w : s.warnings()) out.println("     warning: " + w);
            } catch (ParseException e) {
                failed++;
                out.println("FAIL " + shown);
                for (String error : e.errors()) SpriteFiles.printError(out, error);
            }
        }
        out.printf("%d file(s) checked, %d with errors%n", files.size(), failed);
        return failed == 0 ? 0 : 1;
    }
}
