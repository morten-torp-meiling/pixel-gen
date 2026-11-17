package pixelgen.cli;

import pixelgen.Cli;
import pixelgen.model.Sprite;
import pixelgen.parse.ProjectFiles;
import pixelgen.render.AnimSheet;
import pixelgen.render.ReviewSheet;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * {@code review <file|folder ...> [--strip] [--onion] [--scale N] [--out dir]} writes a review
 * sheet per sprite to {@code <out>/<sprite>/review.png}. Without --scale the frame is enlarged to
 * about 256 px.
 * <p>
 * {@code --strip} instead writes {@code strip.png}: every animation as a row of its steps in play
 * order. {@code --onion} writes {@code onion.png}: the same strips with the previous step's pixels
 * shown in red, to spot parts that jump between frames. Both flags together write both.
 */
public final class ReviewCommand implements Cli.Command {

    private final Path projectDir;

    public ReviewCommand(Path projectDir) { this.projectDir = projectDir; }

    public String usage() { return "review <file|folder ...> [--strip] [--onion] [--scale N] [--out dir]"; }

    public String summary() { return "Write an enlarged review sheet (or animation strips) to preview/<sprite>/"; }

    public int run(List<String> args, PrintStream out, PrintStream err) throws Exception {
        Options o = Options.parse(args, Set.of("--strip", "--onion"), Set.of("--scale", "--out"));
        List<String> targets = o.targets(usage());
        boolean strip = o.has("--strip"), onion = o.has("--onion");
        int scale = o.scale(0);  // 0: automatic
        Path outDir = o.out(projectDir, "preview");

        SpriteFiles.Names names = new SpriteFiles.Names();
        return SpriteFiles.forEach(projectDir, targets, SpriteFiles.ALL, SpriteFiles::load, out, err, (s, shown) -> {
            if ((strip || onion) && s.anims().isEmpty()) {
                out.println("skip " + shown + ": no anims to lay out as strips");
                return true;
            }
            for (Sprite sp : s.withVariants()) {  // each variant gets its own sheet
                names.check(sp.name(), shown, out);
                Path dir = outDir.resolve(sp.name());
                Files.createDirectories(dir);
                int sc = scale == 0 ? ReviewSheet.autoScale(sp) : scale;
                if (!strip && !onion) write(ReviewSheet.render(sp, sc).image(), dir.resolve("review.png"), sc, out);
                if (strip) write(AnimSheet.render(sp, sc, false).image(), dir.resolve("strip.png"), sc, out);
                if (onion) write(AnimSheet.render(sp, sc, true).image(), dir.resolve("onion.png"), sc, out);
            }
            SpriteFiles.printWarnings(out, s.warnings());
            return true;
        }).exitCode();
    }

    private void write(BufferedImage img, Path png, int scale, PrintStream out) throws IOException {
        ImageIO.write(img, "png", png.toFile());
        out.println("wrote " + ProjectFiles.display(projectDir, png) + " (scale " + scale + "x, "
                + img.getWidth() + "x" + img.getHeight() + ")");
    }
}
