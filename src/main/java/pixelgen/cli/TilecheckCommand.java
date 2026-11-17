package pixelgen.cli;

import pixelgen.Cli;
import pixelgen.model.SeamCheck;
import pixelgen.parse.ProjectFiles;
import pixelgen.parse.TilesetParser;
import pixelgen.render.TileCheckSheet;

import javax.imageio.ImageIO;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * {@code tilecheck <file|folder ...> [--scale N] [--out dir]} checks that each tile's edges meet
 * when it repeats (in the directions its {@code wrap} says), and draws every tile repeated to
 * {@code <out>/<tileset>/tilecheck.png}. Exit code 1 when any seam fails.
 */
public final class TilecheckCommand implements Cli.Command {

    private final Path projectDir;

    public TilecheckCommand(Path projectDir) { this.projectDir = projectDir; }

    public String usage() { return "tilecheck <file|folder ...> [--scale N] [--out dir]"; }

    public String summary() { return "Check that tiles repeat without seams; draws preview/<tileset>/tilecheck.png"; }

    public int run(List<String> args, PrintStream out, PrintStream err) throws Exception {
        Options o = Options.parse(args, Set.of(), Set.of("--scale", "--out"));
        List<String> targets = o.targets(usage());
        int scale = o.scale(TileCheckSheet.DEFAULT_SCALE);
        Path outDir = o.out(projectDir, "preview");

        SpriteFiles.Names names = new SpriteFiles.Names();
        return SpriteFiles.forEach(projectDir, targets, SpriteFiles.TILESETS, TilesetParser::parseFile, out, err,
                (t, shown) -> {
                    List<SeamCheck.Seam> seams = SeamCheck.check(t);
                    List<SeamCheck.Seam> bad = seams.stream().filter(s -> !s.ok()).toList();
                    if (bad.isEmpty()) {
                        out.println("ok   " + shown + ": " + t.tiles().size() + " tiles, " + seams.size()
                                + " seams checked");
                    } else {
                        out.println("FAIL " + shown + ": " + bad.size() + " of " + seams.size() + " seams don't match");
                        for (SeamCheck.Seam s : bad) out.println("     " + s.tile() + ": " + s.describe());
                    }
                    names.check(t.name(), shown, out);
                    Path dir = outDir.resolve(t.name());
                    Files.createDirectories(dir);
                    Path png = dir.resolve("tilecheck.png");
                    ImageIO.write(TileCheckSheet.render(t, seams, scale).image(), "png", png.toFile());
                    out.println("     wrote " + ProjectFiles.display(projectDir, png));
                    return bad.isEmpty();
                }).exitCode();
    }
}
