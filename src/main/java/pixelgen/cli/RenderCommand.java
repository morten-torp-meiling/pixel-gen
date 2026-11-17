package pixelgen.cli;

import pixelgen.Cli;
import pixelgen.model.Sprite;
import pixelgen.parse.ProjectFiles;
import pixelgen.render.Renderer;

import javax.imageio.ImageIO;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

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

    public int run(List<String> args, PrintStream out, PrintStream err) throws Exception {
        Options o = Options.parse(args, Set.of(), Set.of("--scale", "--frame", "--out"));
        List<String> targets = o.targets(usage());
        int scale = o.scale(1);
        String onlyFrame = o.get("--frame", null);
        Path outDir = o.out(projectDir, "preview");

        SpriteFiles.Names names = new SpriteFiles.Names();
        return SpriteFiles.forEach(projectDir, targets, SpriteFiles.ALL, SpriteFiles::load, out, err, (s, shown) -> {
            if (onlyFrame != null && s.frame(onlyFrame) == null) {
                out.println("FAIL " + shown + ": no frame '" + onlyFrame + "' (frames: "
                        + String.join(", ", s.frames().keySet()) + ")");
                return false;
            }
            for (Sprite sp : s.withVariants()) {  // each variant gets its own folder
                names.check(sp.name(), shown, out);
                List<Sprite.Frame> frames = onlyFrame == null
                        ? List.copyOf(sp.frames().values())
                        : List.of(sp.frame(onlyFrame));
                Path dir = outDir.resolve(sp.name());
                Files.createDirectories(dir);
                for (Sprite.Frame fr : frames) {
                    Path png = dir.resolve(fr.name() + ".png");
                    ImageIO.write(Renderer.render(fr.grid(), sp.palette(), scale), "png", png.toFile());
                    out.println("wrote " + ProjectFiles.display(projectDir, png)
                            + (scale > 1 ? " (" + sp.width() * scale + "x" + sp.height() * scale + ")" : ""));
                }
            }
            return true;
        }).exitCode();
    }
}
