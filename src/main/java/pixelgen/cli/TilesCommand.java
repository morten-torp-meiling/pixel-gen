package pixelgen.cli;

import pixelgen.Cli;
import pixelgen.godot.TileSetWriter;
import pixelgen.model.Tileset;
import pixelgen.parse.PaletteResolver;
import pixelgen.parse.ParseException;
import pixelgen.parse.TilesetParser;
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
 * {@code tiles <file|folder ...> [--res res://dir] [--padding N] [--extrude N] [--out dir]} writes,
 * for each tileset and variant, {@code <out>/<tileset>.png} (the atlas) and
 * {@code <out>/<tileset>.tres} (a Godot TileSet pointing at {@code <res>/<tileset>.png}).
 */
public final class TilesCommand implements Cli.Command {

    public static final String DEFAULT_RES = "res://tiles";

    private final Path projectDir;

    public TilesCommand(Path projectDir) { this.projectDir = projectDir; }

    public String usage() {
        return "tiles <file|folder ...> [--res res://dir] [--padding N] [--extrude N] [--out dir]";
    }

    public String summary() { return "Write an atlas and a Godot TileSet .tres per tileset, to preview/godot"; }

    public int run(List<String> args, PrintStream out, PrintStream err) throws IOException {
        List<String> targets = new ArrayList<>();
        int padding = 0, extrude = 0;  // Godot pads tiles itself (use_texture_padding)
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
            return usageError(err, "--res must be a Godot folder such as res://tiles, not '" + res + "'");
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
        Files.createDirectories(outDir);
        int failed = 0;
        for (Path f : files) {
            String shown = SpriteFiles.display(projectDir, f);
            Tileset t;
            SheetPacker.Layout layout;
            try {
                t = TilesetParser.parseFile(f, shown, palettes);
                layout = SheetPacker.grid(t.sprite(), t.columns(), padding, extrude);
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
            for (Tileset ts : t.withVariants()) {  // same layout, recoloured pixels
                Path png = outDir.resolve(ts.name() + ".png");
                Path tres = outDir.resolve(ts.name() + ".tres");
                ImageIO.write(SheetPacker.render(ts.sprite(), layout), "png", png.toFile());
                Files.writeString(tres, TileSetWriter.write(ts, layout, resDir + png.getFileName()),
                        StandardCharsets.UTF_8);
                out.println("wrote " + SpriteFiles.display(projectDir, tres) + " and " + png.getFileName() + " ("
                        + ts.tiles().size() + " tiles of " + ts.tileWidth() + "x" + ts.tileHeight() + " in "
                        + layout.rows().size() + "x" + t.columns() + ", atlas at " + resDir + png.getFileName() + ")");
            }
        }
        return failed == 0 ? 0 : 1;
    }

    private static int usageError(PrintStream err, String message) {
        err.println(message);
        return 2;
    }
}
