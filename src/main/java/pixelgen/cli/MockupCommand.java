package pixelgen.cli;

import pixelgen.Cli;
import pixelgen.model.Tileset;
import pixelgen.parse.MockupParser;
import pixelgen.parse.PaletteResolver;
import pixelgen.parse.ParseException;
import pixelgen.parse.TilesetParser;
import pixelgen.render.MockupSheet;
import pixelgen.render.Renderer;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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

    public int run(List<String> args, PrintStream out, PrintStream err) throws IOException {
        List<String> positional = new ArrayList<>();
        int scale = MockupSheet.DEFAULT_SCALE;
        boolean grid = false;
        String variant = null;
        Path outDir = projectDir.resolve("preview");
        for (int i = 0; i < args.size(); i++) {
            String a = args.get(i);
            if (!a.startsWith("--")) {
                positional.add(a);
                continue;
            }
            if (a.equals("--grid")) {
                grid = true;
                continue;
            }
            if (i + 1 >= args.size()) return usageError(err, a + " needs a value");
            String v = args.get(++i);
            switch (a) {
                case "--scale" -> {
                    try {
                        scale = Integer.parseInt(v);
                    } catch (NumberFormatException e) {
                        return usageError(err, "--scale needs a whole number, not '" + v + "'");
                    }
                    if (scale < 1 || scale > Renderer.MAX_SCALE) {
                        return usageError(err, "--scale must be between 1 and " + Renderer.MAX_SCALE);
                    }
                }
                case "--variant" -> variant = v;
                case "--out" -> outDir = projectDir.resolve(v);
                default -> { return usageError(err, "unknown option " + a); }
            }
        }
        if (positional.size() != 2) return usageError(err, "expected: " + usage());
        Path tilesetFile = projectDir.resolve(positional.get(0)), mockFile = projectDir.resolve(positional.get(1));
        for (Path p : List.of(tilesetFile, mockFile)) {
            if (!Files.isRegularFile(p)) {
                err.println("not found: " + SpriteFiles.display(projectDir, p));
                return 1;
            }
        }
        String tsShown = SpriteFiles.display(projectDir, tilesetFile), mockShown = SpriteFiles.display(projectDir, mockFile);
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
        out.println("wrote " + SpriteFiles.display(projectDir, png) + " (" + mock.width() + "x" + mock.height()
                + " tiles, scale " + scale + "x)");
        return 0;
    }

    private static int usageError(PrintStream err, String message) {
        err.println(message);
        return 2;
    }
}
