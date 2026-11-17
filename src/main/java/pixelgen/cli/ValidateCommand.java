package pixelgen.cli;

import pixelgen.Cli;
import pixelgen.model.Sprite;
import pixelgen.model.Tileset;
import pixelgen.parse.SpriteParser;
import pixelgen.parse.TilesetParser;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    public int run(List<String> args, PrintStream out, PrintStream err) throws Exception {
        List<String> words = Options.parse(args, Set.of(), Set.of()).words();
        List<String> targets = words.isEmpty() ? List.of("assets") : words;
        SpriteFiles.Tally tally = SpriteFiles.forEach(projectDir, targets, SpriteFiles.ALL,
                (f, shown, palettes) -> f.toString().endsWith(".tileset")
                        ? TilesetParser.parseFile(f, shown, palettes) : SpriteParser.parseFile(f, shown, palettes),
                out, err, (loaded, shown) -> {
                    Sprite s = loaded instanceof Tileset t ? t.sprite() : (Sprite) loaded;
                    if (loaded instanceof Tileset t) {
                        out.printf("ok   %s (%d tiles of %dx%d, %d parts, %d columns, %spalette %s%s%s)%n", shown,
                                s.frames().size(), s.width(), s.height(), s.parts().size(), t.columns(), style(s),
                                s.palette().name(), oneWayTiles(t), variants(s));
                    } else {
                        out.printf("ok   %s (%dx%d, %d frames, %d parts, %spalette %s%s%s)%n", shown, s.width(),
                                s.height(), s.frames().size(), s.parts().size(), style(s), s.palette().name(),
                                s.anims().isEmpty() ? "" : ", anims " + String.join(" ", s.anims().keySet()),
                                variants(s));
                    }
                    SpriteFiles.printWarnings(out, s.warnings());
                    return true;
                });
        if (tally.files() > 0) out.printf("%d file(s) checked, %d with errors%n", tally.files(), tally.failed());
        return tally.exitCode();
    }

    /** ", wrap x: a b": the tiles that don't repeat both ways, grouped by how they do repeat. */
    private static String oneWayTiles(Tileset t) {
        Map<Tileset.Wrap, List<String>> oneWay = new TreeMap<>();
        for (String tile : t.tiles()) {
            if (t.wrap(tile) != Tileset.Wrap.BOTH) oneWay.computeIfAbsent(t.wrap(tile), k -> new ArrayList<>()).add(tile);
        }
        StringBuilder wraps = new StringBuilder();
        oneWay.forEach((w, tiles) -> wraps.append(", wrap ").append(w.word()).append(": ").append(String.join(" ", tiles)));
        return wraps.toString();
    }

    /** "style cave, ", or nothing for the built-in style. */
    private static String style(Sprite s) {
        return s.style().isBuiltIn() ? "" : "style " + s.style().name() + ", ";
    }

    private static String variants(Sprite s) {
        return s.variants().isEmpty() ? "" : ", variants " + String.join(" ", s.variants().keySet());
    }
}
