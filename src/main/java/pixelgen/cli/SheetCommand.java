package pixelgen.cli;

import pixelgen.Cli;
import pixelgen.model.Sprite;
import pixelgen.parse.PaletteResolver;
import pixelgen.parse.ParseException;
import pixelgen.render.SheetPacker;

import javax.imageio.ImageIO;
import java.awt.Rectangle;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code sheet <file|folder ...> [--padding N] [--extrude N] [--list] [--out dir]} packs each
 * sprite (and each variant) into {@code <out>/<sprite>/sheet.png} at 1:1. {@code --list} also
 * prints every frame's region.
 */
public final class SheetCommand implements Cli.Command {

    public static final int DEFAULT_PADDING = 1;

    private final Path projectDir;

    public SheetCommand(Path projectDir) { this.projectDir = projectDir; }

    public String usage() { return "sheet <file|folder ...> [--padding N] [--extrude N] [--list] [--out dir]"; }

    public String summary() { return "Pack frames into a sprite sheet at preview/<sprite>/sheet.png"; }

    public int run(List<String> args, PrintStream out, PrintStream err) throws IOException {
        List<String> targets = new ArrayList<>();
        int padding = DEFAULT_PADDING, extrude = 0;
        boolean list = false;
        Path outDir = projectDir.resolve("preview");
        for (int i = 0; i < args.size(); i++) {
            String a = args.get(i);
            if (!a.startsWith("--")) {
                targets.add(a);
                continue;
            }
            if (a.equals("--list")) {
                list = true;
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
                case "--out" -> outDir = projectDir.resolve(v);
                default -> { return usageError(err, "unknown option " + a); }
            }
        }
        if (targets.isEmpty()) return usageError(err, "expected: " + usage());
        if (padding < 0 || padding > SheetPacker.MAX_PADDING) {
            return usageError(err, "--padding must be between 0 and " + SheetPacker.MAX_PADDING);
        }
        if (extrude < 0 || extrude * 2 > padding) {
            return usageError(err, "--extrude must be between 0 and half the padding (" + padding / 2
                    + "); use for example --padding 2 --extrude 1");
        }

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
            SheetPacker.Layout layout;
            try {
                s = SpriteFiles.load(f, shown, palettes);
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
                Path dir = outDir.resolve(sp.name());
                Files.createDirectories(dir);
                Path png = dir.resolve("sheet.png");
                ImageIO.write(SheetPacker.render(sp, layout), "png", png.toFile());
                out.println("wrote " + SpriteFiles.display(projectDir, png) + " (" + layout.width() + "x"
                        + layout.height() + ": " + layout.frames().size() + " frames of " + s.width() + "x"
                        + s.height() + " in " + layout.rows().size() + " rows)");
            }
            if (list) {
                for (SheetPacker.Row row : layout.rows()) {
                    out.println("  " + (row.anim() == null ? "(not in an anim)" : row.anim()) + ":");
                    for (String name : row.frames()) {
                        Rectangle r = layout.frames().get(name);
                        out.println("    " + name + " at " + r.x + "," + r.y);
                    }
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
