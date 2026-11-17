package pixelgen.cli;

import pixelgen.Cli;
import pixelgen.godot.Exporter;
import pixelgen.model.Sprite;
import pixelgen.parse.ProjectFiles;
import pixelgen.render.SheetPacker;

import javax.imageio.ImageIO;
import java.awt.Rectangle;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * {@code sheet <file|folder ...> [--padding N] [--extrude N] [--list] [--out dir]} packs each
 * sprite (and each variant) into {@code <out>/<sprite>/sheet.png} at 1:1. {@code --list} also
 * prints every frame's region.
 */
public final class SheetCommand implements Cli.Command {

    private final Path projectDir;

    public SheetCommand(Path projectDir) { this.projectDir = projectDir; }

    public String usage() { return "sheet <file|folder ...> [--padding N] [--extrude N] [--list] [--out dir]"; }

    public String summary() { return "Pack frames into a sprite sheet at preview/<sprite>/sheet.png"; }

    public int run(List<String> args, PrintStream out, PrintStream err) throws Exception {
        Options o = Options.parse(args, Set.of("--list"), Set.of("--padding", "--extrude", "--out"));
        List<String> targets = o.targets(usage());
        Options.Spacing spacing = o.spacing(Exporter.SPRITE_PADDING);
        boolean list = o.has("--list");
        Path outDir = o.out(projectDir, "preview");

        SpriteFiles.Names names = new SpriteFiles.Names();
        return SpriteFiles.forEach(projectDir, targets, SpriteFiles.ALL, SpriteFiles::load, out, err, (s, shown) -> {
            SheetPacker.Layout layout = SheetPacker.layout(s, spacing.padding(), spacing.extrude());
            for (Sprite sp : s.withVariants()) {  // same layout, recoloured pixels
                names.check(sp.name(), shown, out);
                Path dir = outDir.resolve(sp.name());
                Files.createDirectories(dir);
                Path png = dir.resolve("sheet.png");
                ImageIO.write(SheetPacker.render(sp, layout), "png", png.toFile());
                out.println("wrote " + ProjectFiles.display(projectDir, png) + " (" + layout.width() + "x"
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
            return true;
        }).exitCode();
    }
}
