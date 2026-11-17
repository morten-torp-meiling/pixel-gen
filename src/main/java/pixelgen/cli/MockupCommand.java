package pixelgen.cli;

import pixelgen.Cli;
import pixelgen.model.Tileset;
import pixelgen.parse.MockupParser;
import pixelgen.parse.PaletteResolver;
import pixelgen.parse.ParseException;
import pixelgen.parse.ProjectFiles;
import pixelgen.parse.TilesetParser;
import pixelgen.render.MockupSheet;

import javax.imageio.ImageIO;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * {@code mockup <file.tileset> <file.mockup> [--variant v] [--scale N] [--grid] [--out dir]} draws a
 * mock map of the tileset's tiles to {@code <out>/<tileset>/mockup-<mockup name>.png}, to check
 * how different tiles fit next to each other.
 */
public final class MockupCommand implements Cli.Command {

    private final Path projectDir;

    public MockupCommand(Path projectDir) { this.projectDir = projectDir; }

    public String usage() {
        return "mockup <file.tileset> <file.mockup> [--variant v] [--scale N] [--grid] [--out dir]";
    }

    public String summary() { return "Draw a mock map of tiles to preview/<tileset>/mockup-<name>.png"; }

    public int run(List<String> args, PrintStream out, PrintStream err) throws Exception {
        Options o = Options.parse(args, Set.of("--grid"), Set.of("--variant", "--scale", "--out"));
        List<String> positional = o.words();
        if (positional.size() != 2) throw new Cli.UsageException("expected: " + usage());
        int scale = o.scale(MockupSheet.DEFAULT_SCALE);
        boolean grid = o.has("--grid");
        String variant = o.get("--variant", null);
        Path outDir = o.out(projectDir, "preview");
        Path tilesetFile = projectDir.resolve(positional.get(0)), mockFile = projectDir.resolve(positional.get(1));
        for (Path p : List.of(tilesetFile, mockFile)) {
            if (!Files.isRegularFile(p)) {
                err.println("not found: " + ProjectFiles.display(projectDir, p));
                return 1;
            }
        }
        String tsShown = ProjectFiles.display(projectDir, tilesetFile), mockShown = ProjectFiles.display(projectDir, mockFile);
        PaletteResolver palettes = PaletteResolver.cached(projectDir);
        Tileset t;
        MockupParser.Mockup mock;
        try {
            t = TilesetParser.parseFile(tilesetFile, tsShown, palettes);
            mock = MockupParser.parseFile(mockFile, mockShown, t);
        } catch (ParseException e) {
            out.println("FAIL");
            for (String error : e.errors()) SpriteFiles.printError(out, error);
            return 1;
        }
        if (variant != null) {
            String wanted = variant;
            Tileset match = t.withVariants().stream().filter(v -> v.name().equals(wanted)).findFirst().orElse(null);
            if (match == null) {
                err.println("no variant '" + variant + "' in " + tsShown + (t.sprite().variants().isEmpty()
                        ? " (it has none)" : " (variants: " + String.join(", ", t.sprite().variants().keySet()) + ")"));
                return 1;
            }
            t = match;
        }
        String name = mockFile.getFileName().toString().replaceFirst("\\.mockup$", "");
        Path dir = outDir.resolve(t.name());
        Files.createDirectories(dir);
        Path png = dir.resolve("mockup-" + name + ".png");
        MockupSheet.Result r = MockupSheet.render(t, mock, t.name() + "  mock-up " + name, scale, grid);
        ImageIO.write(r.image(), "png", png.toFile());
        out.println("wrote " + ProjectFiles.display(projectDir, png) + " (" + mock.width() + "x" + mock.height()
                + " tiles, scale " + scale + "x)");
        return 0;
    }
}
