package pixelgen.cli;

import pixelgen.Cli;
import pixelgen.model.Anim;
import pixelgen.model.Colors;
import pixelgen.model.Sprite;
import pixelgen.parse.PaletteResolver;
import pixelgen.parse.ParseException;
import pixelgen.parse.SpriteParser;
import pixelgen.render.GifWriter;
import pixelgen.render.Renderer;

import java.awt.Color;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code gif <file|folder ...> [--anim name] [--scale N] [--bg #rrggbb|none] [--out dir]} writes
 * each animation as {@code <out>/<sprite>/<anim>.gif}, for the sprite and each of its variants.
 */
public final class GifCommand implements Cli.Command {

    /** A muted ground colour: dark outlines and the soft shadow both show against it. */
    public static final String DEFAULT_BACKGROUND = "#c4bdb0";
    public static final int DEFAULT_SCALE = 4;

    private final Path projectDir;

    public GifCommand(Path projectDir) { this.projectDir = projectDir; }

    public String usage() {
        return "gif <file|folder ...> [--anim name] [--scale N] [--bg #rrggbb|none] [--out dir]";
    }

    public String summary() { return "Write each animation as preview/<sprite>/<anim>.gif"; }

    public int run(List<String> args, PrintStream out, PrintStream err) throws IOException {
        List<String> targets = new ArrayList<>();
        int scale = DEFAULT_SCALE;
        String onlyAnim = null;
        Color background = new Color(Colors.parseHex(DEFAULT_BACKGROUND));
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
                case "--anim" -> onlyAnim = v;
                case "--bg" -> {
                    if (v.equals("none")) {
                        background = null;
                    } else {
                        try {
                            background = new Color(Colors.parseHex(v));
                        } catch (IllegalArgumentException e) {
                            return usageError(err, "--bg needs #rrggbb or none, not '" + v + "'");
                        }
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
        int failed = 0, written = 0;
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
            if (s.anims().isEmpty()) {
                out.println("skip " + shown + ": no anims");
                continue;
            }
            if (onlyAnim != null && s.anim(onlyAnim) == null) {
                failed++;
                out.println("FAIL " + shown + ": no anim '" + onlyAnim + "' (anims: "
                        + String.join(", ", s.anims().keySet()) + ")");
                continue;
            }
            for (Sprite sp : s.withVariants()) {
                List<Anim> anims = onlyAnim == null ? List.copyOf(sp.anims().values()) : List.of(sp.anim(onlyAnim));
                Path dir = outDir.resolve(sp.name());
                Files.createDirectories(dir);
                for (Anim anim : anims) {
                    GifWriter.Animation gif = GifWriter.animation(sp, anim, scale, background);
                    Path file = dir.resolve(anim.name() + ".gif");
                    GifWriter.write(gif, file);
                    written++;
                    out.printf(Locale.ROOT, "wrote %s (%d frames, %s fps, %s, %.2f s%s)%n",
                            SpriteFiles.display(projectDir, file), anim.steps().size(), fps(anim.fps()),
                            anim.loop() ? "loop" : "once", anim.seconds(),
                            gif.delays().stream().anyMatch(d -> d < 2)
                                    ? "; frames under 0.02 s play slower in most browsers" : "");
                }
            }
        }
        if (written == 0 && failed == 0) {
            err.println("no anims found; add 'anim <name> fps <n> loop: <frames>' to a sprite");
            return 1;
        }
        return failed == 0 ? 0 : 1;
    }

    private static String fps(double fps) {
        return fps == Math.rint(fps) ? String.valueOf((long) fps) : String.valueOf(fps);
    }

    private static int usageError(PrintStream err, String message) {
        err.println(message);
        return 2;
    }
}
