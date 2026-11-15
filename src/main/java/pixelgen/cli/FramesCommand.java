package pixelgen.cli;

import pixelgen.Cli;
import pixelgen.godot.SpriteFramesWriter;
import pixelgen.model.Sprite;
import pixelgen.parse.PaletteResolver;
import pixelgen.parse.ParseException;
import pixelgen.parse.SpriteParser;
import pixelgen.render.SheetPacker;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code frames <file|folder ...> [--res res://dir] [--padding N] [--extrude N] [--out dir]} writes,
 * for each sprite and variant, {@code <out>/<sprite>.png} (the sheet) and {@code <out>/<sprite>.tres}
 * (a Godot SpriteFrames resource pointing at {@code <res>/<sprite>.png}). Copy both into that
 * folder of the Godot project.
 */
public final class FramesCommand implements Cli.Command {

    public static final String DEFAULT_RES = "res://sprites";

    private final Path projectDir;

    public FramesCommand(Path projectDir) { this.projectDir = projectDir; }

    public String usage() {
        return "frames <file|folder ...> [--res res://dir] [--padding N] [--extrude N] [--out dir]";
    }

    public String summary() { return "Write a sheet and a Godot SpriteFrames .tres per sprite, to preview/godot"; }

    public int run(List<String> args, PrintStream out, PrintStream err) throws IOException {
        List<String> targets = new ArrayList<>();
        int padding = SheetCommand.DEFAULT_PADDING, extrude = 0;
        String res = DEFAULT_RES;
        Path outDir = projectDir.resolve("preview/godot");
        for (int i = 0; i < args.size(); i++) {
            String a = args.get(i);
            if (!a.startsWith("--")) {
                targets.add(a);
                continue;
            }
            if (i + 1 >= args.size()) return usageError(err, a + " needs a value");
            String v = args.get(++i);
            switch (a) {
                case "--padding", "--extrude" -> {
                    int n;
                    try {
                        n = Integer.parseInt(v);
                    } catch (NumberFormatException e) {
                        return usageError(err, a + " needs a whole number, not '" + v + "'");
                    }
                    if (a.equals("--padding")) padding = n;
                    else extrude = n;
                }
                case "--res" -> res = v;
                case "--out" -> outDir = projectDir.resolve(v);
                default -> { return usageError(err, "unknown option " + a); }
            }
        }
        if (targets.isEmpty()) return usageError(err, "expected: " + usage());
        if (!res.startsWith("res://") || res.contains("\\")) {
            return usageError(err, "--res must be a Godot folder such as res://sprites, not '" + res + "'");
        }
        if (padding < 0 || padding > SheetPacker.MAX_PADDING) {
            return usageError(err, "--padding must be between 0 and " + SheetPacker.MAX_PADDING);
        }
        if (extrude < 0 || extrude * 2 > padding) {
            return usageError(err, "--extrude must be between 0 and half the padding (" + padding / 2
                    + "); use for example --padding 2 --extrude 1");
        }
        String resDir = res.endsWith("/") ? res : res + "/";

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
        Files.createDirectories(outDir);
        int failed = 0;
        for (Path f : files) {
            String shown = SpriteFiles.display(projectDir, f);
            Sprite s;
            SheetPacker.Layout layout;
            try {
                s = SpriteParser.parseFile(f, shown, palettes);
                layout = SheetPacker.layout(s, padding, extrude);
            } catch (ParseException e) {
                failed++;
                out.println("FAIL " + shown);
                for (String error : e.errors()) SpriteFiles.printError(out, error);
                continue;
            } catch (IllegalArgumentException e) {
                failed++;
                out.println("FAIL " + shown + ": " + e.getMessage());
                continue;
            }
            for (Sprite sp : s.withVariants()) {  // same layout, recoloured pixels
                Path png = outDir.resolve(sp.name() + ".png");
                Path tres = outDir.resolve(sp.name() + ".tres");
                ImageIO.write(SheetPacker.render(sp, layout), "png", png.toFile());
                SpriteFramesWriter.Result r = SpriteFramesWriter.write(sp, layout, resDir + sp.name() + ".png");
                Files.writeString(tres, r.text(), StandardCharsets.UTF_8);
                out.println("wrote " + SpriteFiles.display(projectDir, tres) + " and " + png.getFileName() + " ("
                        + r.animations().size() + " animations, " + (layout.frames().size() - r.skipped().size()) + " frames, sheet at "
                        + resDir + png.getFileName() + ")");
                for (String frame : r.skipped()) {
                    out.println("  note: frame '" + frame + "' is in no animation, and an animation already has"
                            + " that name, so it isn't exported");
                }
            }
            out.println("  in Godot: AnimatedSprite2D with centered off and offset (" + -s.anchorX() + ", "
                    + -s.anchorY() + ") puts the anchor on the node; set texture filter to Nearest");
        }
        return failed == 0 ? 0 : 1;
    }

    private static int usageError(PrintStream err, String message) {
        err.println(message);
        return 2;
    }
}
