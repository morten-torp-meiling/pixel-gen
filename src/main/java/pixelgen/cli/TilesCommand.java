package pixelgen.cli;

import pixelgen.Cli;
import pixelgen.godot.TileSetWriter;
import pixelgen.model.Tileset;
import pixelgen.parse.ProjectFiles;
import pixelgen.parse.TilesetParser;
import pixelgen.render.SheetPacker;

import javax.imageio.ImageIO;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

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

    public int run(List<String> args, PrintStream out, PrintStream err) throws Exception {
        Options o = Options.parse(args, Set.of(), Set.of("--res", "--padding", "--extrude", "--out"));
        List<String> targets = o.targets(usage());
        String resDir = o.resDir(DEFAULT_RES);
        Options.Spacing spacing = o.spacing(0);  // Godot pads tiles itself (use_texture_padding)
        Path outDir = o.out(projectDir, "preview/godot");
        Files.createDirectories(outDir);

        SpriteFiles.Names names = new SpriteFiles.Names();
        return SpriteFiles.forEach(projectDir, targets, SpriteFiles.TILESETS, TilesetParser::parseFile, out, err,
                (t, shown) -> {
                    SheetPacker.Layout layout = SheetPacker.grid(t.sprite(), t.columns(), spacing.padding(),
                            spacing.extrude());
                    for (Tileset ts : t.withVariants()) {  // same layout, recoloured pixels
                        names.check(ts.name(), shown, out);
                        Path png = outDir.resolve(ts.name() + ".png");
                        Path tres = outDir.resolve(ts.name() + ".tres");
                        ImageIO.write(SheetPacker.render(ts.sprite(), layout), "png", png.toFile());
                        Files.writeString(tres, TileSetWriter.write(ts, layout, resDir + png.getFileName()),
                                StandardCharsets.UTF_8);
                        out.println("wrote " + ProjectFiles.display(projectDir, tres) + " and " + png.getFileName()
                                + " (" + ts.tiles().size() + " tiles of " + ts.tileWidth() + "x" + ts.tileHeight()
                                + " in " + layout.rows().size() + "x" + t.columns() + ", atlas at " + resDir
                                + png.getFileName() + ")");
                    }
                    return true;
                }).exitCode();
    }
}
