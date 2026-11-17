package pixelgen.cli;

import pixelgen.Cli;
import pixelgen.model.Colors;
import pixelgen.model.Palette;
import pixelgen.parse.HexPaletteImporter;
import pixelgen.parse.Palettes;
import pixelgen.parse.ProjectFiles;
import pixelgen.render.PaletteSwatch;

import javax.imageio.ImageIO;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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
        Options o = Options.parse(args, Set.of("--force"), Set.of("--out"));
        List<String> words = o.words();
        if (!words.isEmpty() && words.getFirst().equals("import")) {
            if (words.size() != 3) throw new Cli.UsageException("expected: palette import <file.hex> <name> [--force]");
            return importHex(words.get(1), words.get(2), o.has("--force"), out, err);
        }
        if (words.size() != 1) throw new Cli.UsageException("expected: palette <name|file.pal> [--out dir]");
        return show(words.getFirst(), o.out(projectDir, "preview"), out, err);
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
        out.println("swatch: " + ProjectFiles.display(projectDir, png));
        for (String w : p.warnings()) err.println("warning: " + w);
        return 0;
    }

    private int importHex(String hexFile, String name, boolean force, PrintStream out, PrintStream err)
            throws Exception {
        Path source = projectDir.resolve(hexFile);
        Path target = Palettes.resolve(name, projectDir);
        if (Files.exists(target) && !force) {
            err.println(ProjectFiles.display(projectDir, target) + " already exists; add --force to overwrite it");
            return 1;
        }
        String pal = HexPaletteImporter.toPal(Files.readString(source, StandardCharsets.UTF_8), hexFile, name);
        Files.createDirectories(target.getParent());
        Files.writeString(target, pal, StandardCharsets.UTF_8);
        out.println("wrote " + ProjectFiles.display(projectDir, target));
        return 0;
    }
}
