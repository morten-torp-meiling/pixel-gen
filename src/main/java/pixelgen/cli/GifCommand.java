package pixelgen.cli;

import pixelgen.Cli;
import pixelgen.model.Anim;
import pixelgen.model.Colors;
import pixelgen.model.Sprite;
import pixelgen.parse.ProjectFiles;
import pixelgen.parse.SpriteParser;
import pixelgen.render.GifWriter;

import java.awt.Color;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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

    public int run(List<String> args, PrintStream out, PrintStream err) throws Exception {
        Options o = Options.parse(args, Set.of(), Set.of("--anim", "--scale", "--bg", "--out"));
        List<String> targets = o.targets(usage());
        int scale = o.scale(DEFAULT_SCALE);
        String onlyAnim = o.get("--anim", null);
        Color background = background(o.get("--bg", DEFAULT_BACKGROUND));
        Path outDir = o.out(projectDir, "preview");

        SpriteFiles.Names names = new SpriteFiles.Names();
        int[] written = {0};
        SpriteFiles.Tally tally = SpriteFiles.forEach(projectDir, targets, SpriteFiles.SPRITES, SpriteParser::parseFile,
                out, err, (s, shown) -> {
                    if (s.anims().isEmpty()) {
                        out.println("skip " + shown + ": no anims");
                        return true;
                    }
                    if (onlyAnim != null && s.anim(onlyAnim) == null) {
                        out.println("FAIL " + shown + ": no anim '" + onlyAnim + "' (anims: "
                                + String.join(", ", s.anims().keySet()) + ")");
                        return false;
                    }
                    for (Sprite sp : s.withVariants()) {
                        names.check(sp.name(), shown, out);
                        List<Anim> anims = onlyAnim == null ? List.copyOf(sp.anims().values())
                                : List.of(sp.anim(onlyAnim));
                        Path dir = outDir.resolve(sp.name());
                        Files.createDirectories(dir);
                        for (Anim anim : anims) {
                            GifWriter.Animation gif = GifWriter.animation(sp, anim, scale, background);
                            Path file = dir.resolve(anim.name() + ".gif");
                            GifWriter.write(gif, file);
                            written[0]++;
                            out.printf(Locale.ROOT, "wrote %s (%d frames, %s fps, %s, %.2f s%s)%n",
                                    ProjectFiles.display(projectDir, file), anim.steps().size(), Anim.number(anim.fps()),
                                    anim.loop() ? "loop" : "once", anim.seconds(),
                                    gif.delays().stream().anyMatch(d -> d < 2)
                                            ? "; frames under 0.02 s play slower in most browsers" : "");
                        }
                    }
                    return true;
                });
        if (tally.files() > 0 && written[0] == 0 && tally.failed() == 0) {
            err.println("no anims found; add 'anim <name> fps <n> loop: <frames>' to a sprite");
            return 1;
        }
        return tally.exitCode();
    }

    /** {@code --bg}: a colour, or null for {@code none} (a transparent GIF). */
    private static Color background(String value) throws Cli.UsageException {
        if (value.equals("none")) return null;
        try {
            return new Color(Colors.parseHex(value));
        } catch (IllegalArgumentException e) {
            throw new Cli.UsageException("--bg needs #rrggbb or none, not '" + value + "'");
        }
    }
}
