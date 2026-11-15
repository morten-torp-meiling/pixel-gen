package pixelgen.cli;

import pixelgen.Cli;
import pixelgen.model.SeamCheck;
import pixelgen.model.Tileset;
import pixelgen.parse.PaletteResolver;
import pixelgen.parse.ParseException;
import pixelgen.parse.TilesetParser;
import pixelgen.render.Renderer;
import pixelgen.render.TileCheckSheet;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code tilecheck <file|folder ...> [--scale N] [--out dir]} checks that each tile's edges meet
 * when it repeats (in the directions its {@code wrap} says), and draws every tile repeated to
 * {@code <out>/<tileset>/tilecheck.png}. Exit code 1 when any seam fails.
 */
public final class TilecheckCommand implements Cli.Command {

    private final Path projectDir;

    public TilecheckCommand(Path projectDir) { this.projectDir = projectDir; }

    public String usage() { return "tilecheck <file|folder ...> [--scale N] [--out dir]"; }

    public String summary() { return "Check that tiles repeat without seams; draws preview/<tileset>/tilecheck.png"; }

    public int run(List<String> args, PrintStream out, PrintStream err) throws IOException {
        List<String> targets = new ArrayList<>();
        int scale = TileCheckSheet.DEFAULT_SCALE;
        Path outDir = projectDir.resolve("preview");
        for (int i = 0; i < args.size(); i++) {
            String a = args.get(i);
            if (!a.startsWith("--")) {
                targets.add(a);
                continue;
            }
            if (i + 1 >= args.size()) return usageError(err, a + " needs a value");
            String v = args.get(++i);
            switch (a) {
                case "--scale" -> {
                    try {
                        scale = Integer.parseInt(v);
                    } catch (NumberFormatException e) {
                        return usageError(err, "--scale needs a whole number, not '" + v + "'");
                    }
                    if (scale < 1 || scale > Renderer.MAX_SCALE) {
                        return usageError(err, "--scale must be between 1 and " + Renderer.MAX_SCALE);
                    }
                }
                case "--out" -> outDir = projectDir.resolve(v);
                default -> { return usageError(err, "unknown option " + a); }
            }
        }
        if (targets.isEmpty()) return usageError(err, "expected: " + usage());

        List<Path> files;
        try {
            files = SpriteFiles.collect(projectDir, targets, SpriteFiles.TILESETS);
        } catch (NoSuchFileException e) {
            err.println("not found: " + e.getFile());
            return 1;
        }
        if (files.isEmpty()) {
            err.println(SpriteFiles.noneFound(SpriteFiles.TILESETS, targets));
            return 1;
        }

        PaletteResolver palettes = PaletteResolver.cached(projectDir);
        int failed = 0;
        for (Path f : files) {
            String shown = SpriteFiles.display(projectDir, f);
            Tileset t;
            try {
                t = TilesetParser.parseFile(f, shown, palettes);
            } catch (ParseException e) {
                failed++;
                out.println("FAIL " + shown);
                for (String error : e.errors()) SpriteFiles.printError(out, error);
                continue;
            }
            List<SeamCheck.Seam> seams = SeamCheck.check(t);
            List<SeamCheck.Seam> bad = seams.stream().filter(s -> !s.ok()).toList();
            if (bad.isEmpty()) {
                out.println("ok   " + shown + ": " + t.tiles().size() + " tiles, " + seams.size() + " seams checked");
            } else {
                failed++;
                out.println("FAIL " + shown + ": " + bad.size() + " of " + seams.size() + " seams don't match");
                for (SeamCheck.Seam s : bad) out.println("     " + s.tile() + ": " + s.describe());
            }
            Path dir = outDir.resolve(t.name());
            Files.createDirectories(dir);
            Path png = dir.resolve("tilecheck.png");
            ImageIO.write(TileCheckSheet.render(t, seams, scale).image(), "png", png.toFile());
            out.println("     wrote " + SpriteFiles.display(projectDir, png));
        }
        return failed == 0 ? 0 : 1;
    }

    private static int usageError(PrintStream err, String message) {
        err.println(message);
        return 2;
    }
}
