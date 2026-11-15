package pixelgen.cli;

import pixelgen.Cli;
import pixelgen.model.Sprite;
import pixelgen.parse.PaletteResolver;
import pixelgen.parse.ParseException;
import pixelgen.parse.SpriteParser;
import pixelgen.render.Renderer;
import pixelgen.render.ReviewSheet;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code review <file|folder ...> [--scale N] [--out dir]} writes a review sheet per sprite to
 * {@code <out>/<sprite>/review.png}. Without --scale the frame is enlarged to about 256 px.
 */
public final class ReviewCommand implements Cli.Command {

    private final Path projectDir;

    public ReviewCommand(Path projectDir) { this.projectDir = projectDir; }

    public String usage() { return "review <file|folder ...> [--scale N] [--out dir]"; }

    public String summary() { return "Write an enlarged review sheet to preview/<sprite>/review.png"; }

    public int run(List<String> args, PrintStream out, PrintStream err) throws IOException {
        List<String> targets = new ArrayList<>();
        Integer scale = null;
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
            Sprite s;
            try {
                s = SpriteParser.parseFile(f, shown, palettes);
            } catch (ParseException e) {
                failed++;
                out.println("FAIL " + shown);
                for (String error : e.errors()) SpriteFiles.printError(out, error);
                continue;
            }
            for (Sprite sp : s.withVariants()) {  // each variant gets its own sheet
                ReviewSheet.Result r = scale == null ? ReviewSheet.render(sp) : ReviewSheet.render(sp, scale);
                Path dir = outDir.resolve(sp.name());
                Files.createDirectories(dir);
                Path png = dir.resolve("review.png");
                ImageIO.write(r.image(), "png", png.toFile());
                out.println("wrote " + SpriteFiles.display(projectDir, png) + " (scale " + r.scale() + "x, "
                        + r.image().getWidth() + "x" + r.image().getHeight() + ")");
            }
            for (String w : s.warnings()) out.println("     warning: " + w);
        }
        return failed == 0 ? 0 : 1;
    }

    private static int usageError(PrintStream err, String message) {
        err.println(message);
        return 2;
    }
}
