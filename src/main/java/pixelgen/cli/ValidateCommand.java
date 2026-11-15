package pixelgen.cli;

import pixelgen.Cli;
import pixelgen.model.Sprite;
import pixelgen.model.Tileset;
import pixelgen.parse.PaletteResolver;
import pixelgen.parse.ParseException;
import pixelgen.parse.TilesetParser;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * {@code validate [file|folder ...]} checks sprite and tileset files and reports every error with its line.
 * Folders are searched for {@code .sprite} and {@code .tileset} files; with no arguments, {@code assets/} is checked.
 * Exit code 0 when all files are valid, 1 when any has errors.
 */
public final class ValidateCommand implements Cli.Command {

    private final Path projectDir;

    public ValidateCommand(Path projectDir) { this.projectDir = projectDir; }

    public String usage() { return "validate [file|folder ...]"; }

    public String summary() { return "Check .sprite and .tileset files (default: everything under assets/)"; }

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
            err.println(SpriteFiles.noneFound(SpriteFiles.ALL, targets));
            return 1;
        }

        PaletteResolver palettes = PaletteResolver.cached(projectDir);
        int failed = 0;
        for (Path f : files) {
            String shown = SpriteFiles.display(projectDir, f);
            try {
                Sprite s;
                String variants;
                if (f.toString().endsWith(".tileset")) {
                    Tileset t = TilesetParser.parseFile(f, shown, palettes);
                    s = t.sprite();
                    variants = variants(s);
                    // Tiles that don't repeat both ways, grouped by how they do repeat.
                    Map<Tileset.Wrap, List<String>> oneWay = new TreeMap<>();
                    for (String tile : t.tiles()) {
                        if (t.wrap(tile) != Tileset.Wrap.BOTH) {
                            oneWay.computeIfAbsent(t.wrap(tile), k -> new ArrayList<>()).add(tile);
                        }
                    }
                    StringBuilder wraps = new StringBuilder();
                    oneWay.forEach((w, tiles) -> wraps.append(", wrap ").append(w.word()).append(": ")
                            .append(String.join(" ", tiles)));
                    out.printf("ok   %s (%d tiles of %dx%d, %d parts, %d columns, palette %s%s%s)%n", shown,
                            s.frames().size(), s.width(), s.height(), s.parts().size(), t.columns(),
                            s.palette().name(), wraps, variants);
                } else {
                    s = SpriteFiles.load(f, shown, palettes);
                    out.printf("ok   %s (%dx%d, %d frames, %d parts, palette %s%s%s)%n", shown, s.width(), s.height(),
                            s.frames().size(), s.parts().size(), s.palette().name(),
                            s.anims().isEmpty() ? "" : ", anims " + String.join(" ", s.anims().keySet()), variants(s));
                }
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

    private static String variants(Sprite s) {
        return s.variants().isEmpty() ? "" : ", variants " + String.join(" ", s.variants().keySet());
    }
}
