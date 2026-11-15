package pixelgen.cli;

import pixelgen.Cli;
import pixelgen.model.Sprite;
import pixelgen.parse.PaletteResolver;
import pixelgen.parse.ParseException;
import pixelgen.render.Renderer;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code render <file|folder ...> [--scale N] [--frame name] [--out dir]} writes one PNG per frame
 * to {@code <out>/<sprite>/<frame>.png}. The default output folder is {@code preview}, the default
 * scale is 1.
 */
public final class RenderCommand implements Cli.Command {

    private final Path projectDir;

    public RenderCommand(Path projectDir) { this.projectDir = projectDir; }

    public String usage() { return "render <file|folder ...> [--scale N] [--frame name] [--out dir]"; }

    public String summary() { return "Write each frame as a PNG to preview/<sprite>/<frame>.png"; }

    public int run(List<String> args, PrintStream out, PrintStream err) throws IOException {
        List<String> targets = new ArrayList<>();
        int scale = 1;
        String onlyFrame = null;
        Path outDir = projectDir.resolve("preview");
        for (int i = 0; i < args.size(); i++) {
            String a = args.get(i);
            if (a.startsWith("--")) {
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
                    case "--frame" -> onlyFrame = v;
                    case "--out" -> outDir = projectDir.resolve(v);
                    default -> { return usageError(err, "unknown option " + a); }
                }
            } else {
                targets.add(a);
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
            err.println(SpriteFiles.noneFound(SpriteFiles.ALL, targets));
            return 1;
        }

        PaletteResolver palettes = PaletteResolver.cached(projectDir);
        int failed = 0;
        for (Path f : files) {
            String shown = SpriteFiles.display(projectDir, f);
            Sprite s;
            try {
                s = SpriteFiles.load(f, shown, palettes);
            } catch (ParseException e) {
                failed++;
                out.println("FAIL " + shown);
                for (String error : e.errors()) SpriteFiles.printError(out, error);
                continue;
            }
            if (onlyFrame != null && s.frame(onlyFrame) == null) {
                failed++;
                out.println("FAIL " + shown + ": no frame '" + onlyFrame + "' (frames: "
                        + String.join(", ", s.frames().keySet()) + ")");
                continue;
            }
            for (Sprite sp : s.withVariants()) {  // each variant gets its own folder
                List<Sprite.Frame> frames = onlyFrame == null
                        ? List.copyOf(sp.frames().values())
                        : List.of(sp.frame(onlyFrame));
                Path dir = outDir.resolve(sp.name());
                Files.createDirectories(dir);
                for (Sprite.Frame fr : frames) {
                    Path png = dir.resolve(fr.name() + ".png");
                    ImageIO.write(Renderer.render(fr.grid(), sp.palette(), scale), "png", png.toFile());
                    out.println("wrote " + SpriteFiles.display(projectDir, png)
                            + (scale > 1 ? " (" + sp.width() * scale + "x" + sp.height() * scale + ")" : ""));
                }
            }
        }
        return failed == 0 ? 0 : 1;
    }

    private static int usageError(PrintStream err, String message) {
        err.println(message);
        return 2;
    }
}
