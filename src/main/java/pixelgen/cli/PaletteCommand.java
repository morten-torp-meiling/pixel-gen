package pixelgen.cli;

import pixelgen.Cli;
import pixelgen.model.Colors;
import pixelgen.model.Palette;
import pixelgen.parse.HexPaletteImporter;
import pixelgen.parse.Palettes;
import pixelgen.render.PaletteSwatch;

import javax.imageio.ImageIO;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code palette <name|file.pal> [--out dir]} checks a palette, lists it and writes a swatch PNG.
 * {@code palette import <file.hex> <name> [--force]} creates {@code palettes/<name>.pal}.
 */
public final class PaletteCommand implements Cli.Command {

    private final Path projectDir;

    public PaletteCommand(Path projectDir) { this.projectDir = projectDir; }

    public String usage() { return "palette <name> | palette import <file.hex> <name>"; }

    public String summary() { return "Check a palette and render its swatch, or import a Lospec .hex"; }

    public int run(List<String> args, PrintStream out, PrintStream err) throws Exception {
        List<String> rest = new ArrayList<>(args);
        boolean force = rest.remove("--force");
        Path outDir = projectDir.resolve("preview");
        int outFlag = rest.indexOf("--out");
        if (outFlag >= 0) {
            if (outFlag + 1 >= rest.size()) return usageError(err, "--out needs a folder");
            outDir = projectDir.resolve(rest.get(outFlag + 1));
            rest.subList(outFlag, outFlag + 2).clear();
        }
        if (!rest.isEmpty() && rest.getFirst().equals("import")) {
            if (rest.size() != 3) return usageError(err, "expected: palette import <file.hex> <name>");
            return importHex(rest.get(1), rest.get(2), force, out, err);
        }
        if (rest.size() != 1) return usageError(err, "expected: palette <name|file.pal>");
        return show(rest.getFirst(), outDir, out, err);
    }

    private int show(String nameOrPath, Path outDir, PrintStream out, PrintStream err) throws Exception {
        Palette p = Palettes.load(nameOrPath, projectDir);
        out.printf("palette %s: %d colours in %d ramps%n", p.name(), p.size(), p.ramps().size());
        for (Palette.Ramp r : p.ramps()) {
            StringBuilder line = new StringBuilder();
            for (Palette.Entry e : r.entries()) {
                line.append(String.format(Locale.ROOT, "  %c %s L=%.2f", e.symbol(), e.hex(),
                        Colors.lightness(e.rgb())));
            }
            out.printf("  %-12s%s%s%n", r.name(), r.ordered() ? "" : " (unordered)", line);
        }
        Files.createDirectories(outDir);
        Path png = outDir.resolve("palette-" + p.name() + ".png");
        ImageIO.write(PaletteSwatch.render(p), "png", png.toFile());
        out.println("swatch: " + png);
        for (String w : p.warnings()) err.println("warning: " + w);
        return 0;
    }

    private int importHex(String hexFile, String name, boolean force, PrintStream out, PrintStream err)
            throws Exception {
        Path source = projectDir.resolve(hexFile);
        Path target = Palettes.resolve(name, projectDir);
        if (Files.exists(target) && !force) {
            err.println(target + " already exists; add --force to overwrite it");
            return 1;
        }
        String pal = HexPaletteImporter.toPal(Files.readString(source, StandardCharsets.UTF_8), hexFile, name);
        Files.createDirectories(target.getParent());
        Files.writeString(target, pal, StandardCharsets.UTF_8);
        out.println("wrote " + target);
        return 0;
    }

    private static int usageError(PrintStream err, String message) {
        err.println(message);
        return 2;
    }
}
