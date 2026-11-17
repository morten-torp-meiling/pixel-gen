package pixelgen.cli;

import pixelgen.Cli;
import pixelgen.godot.Exporter;
import pixelgen.godot.SpriteFramesWriter;
import pixelgen.model.Sprite;
import pixelgen.parse.ProjectFiles;
import pixelgen.parse.SpriteParser;
import pixelgen.render.SheetPacker;

import javax.imageio.ImageIO;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

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

    public int run(List<String> args, PrintStream out, PrintStream err) throws Exception {
        Options o = Options.parse(args, Set.of(), Set.of("--res", "--padding", "--extrude", "--out"));
        List<String> targets = o.targets(usage());
        String resDir = o.resDir(DEFAULT_RES);
        Options.Spacing spacing = o.spacing(Exporter.SPRITE_PADDING);
        Path outDir = o.out(projectDir, "preview/godot");
        Files.createDirectories(outDir);

        SpriteFiles.Names names = new SpriteFiles.Names();
        return SpriteFiles.forEach(projectDir, targets, SpriteFiles.SPRITES, SpriteParser::parseFile, out, err,
                (s, shown) -> {
                    SheetPacker.Layout layout = SheetPacker.layout(s, spacing.padding(), spacing.extrude());
                    for (Sprite sp : s.withVariants()) {  // same layout, recoloured pixels
                        names.check(sp.name(), shown, out);
                        Path png = outDir.resolve(sp.name() + ".png");
                        Path tres = outDir.resolve(sp.name() + ".tres");
                        ImageIO.write(SheetPacker.render(sp, layout), "png", png.toFile());
                        SpriteFramesWriter.Result r = SpriteFramesWriter.write(sp, layout, resDir + png.getFileName());
                        Files.writeString(tres, r.text(), StandardCharsets.UTF_8);
                        out.println("wrote " + ProjectFiles.display(projectDir, tres) + " and " + png.getFileName()
                                + " (" + r.animations().size() + " animations, "
                                + (layout.frames().size() - r.skipped().size()) + " frames, sheet at "
                                + resDir + png.getFileName() + ")");
                        for (String frame : r.skipped()) {
                            out.println("  note: frame '" + frame + "' is in no animation, and an animation already"
                                    + " has that name, so it isn't exported");
                        }
                    }
                    out.println("  in Godot: AnimatedSprite2D with centered off and offset (" + -s.anchorX() + ", "
                            + -s.anchorY() + ") puts the anchor on the node; set texture filter to Nearest");
                    return true;
                }).exitCode();
    }
}
