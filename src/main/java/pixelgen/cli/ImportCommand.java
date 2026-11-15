package pixelgen.cli;

import pixelgen.Cli;
import pixelgen.model.Colors;
import pixelgen.model.Palette;
import pixelgen.parse.PaletteResolver;
import pixelgen.parse.ParseException;
import pixelgen.parse.PngImporter;
import pixelgen.parse.SpriteParser;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code import <image.png> --palette <name> [--name n] [--frame f] [--scale N] [--out file.sprite] [--force]}
 * converts an image to a one-frame sprite. Without --out the sprite text goes to standard output
 * and the colour report to standard error.
 */
public final class ImportCommand implements Cli.Command {

    private final Path projectDir;

    public ImportCommand(Path projectDir) { this.projectDir = projectDir; }

    public String usage() {
        return "import <image.png> --palette <name> [--name n] [--frame f] [--scale N] [--out file] [--force]";
    }

    public String summary() { return "Convert a PNG to .sprite text, mapping colours to the palette"; }

    public int run(List<String> args, PrintStream out, PrintStream err) throws IOException {
        List<String> positional = new ArrayList<>();
        String paletteName = null, name = null, frame = "idle", outFile = null;
        Integer scale = null;
        boolean force = false;
        for (int i = 0; i < args.size(); i++) {
            String a = args.get(i);
            if (a.equals("--force")) {
                force = true;
                continue;
            }
            if (!a.startsWith("--")) {
                positional.add(a);
                continue;
            }
            if (i + 1 >= args.size()) return usageError(err, a + " needs a value");
            String v = args.get(++i);
            switch (a) {
                case "--palette" -> paletteName = v;
                case "--name" -> name = v;
                case "--frame" -> frame = v;
                case "--out" -> outFile = v;
                case "--scale" -> {
                    try {
                        scale = Integer.parseInt(v);
                    } catch (NumberFormatException e) {
                        return usageError(err, "--scale needs a whole number, not '" + v + "'");
                    }
                }
                default -> { return usageError(err, "unknown option " + a); }
            }
        }
        if (positional.size() != 1 || paletteName == null) return usageError(err, "expected: " + usage());

        Path png = projectDir.resolve(positional.getFirst());
        if (!Files.isRegularFile(png)) {
            err.println("not found: " + positional.getFirst());
            return 1;
        }
        BufferedImage img = ImageIO.read(png.toFile());
        if (img == null) {
            err.println("not an image Java can read: " + positional.getFirst());
            return 1;
        }
        Palette palette;
        try {
            palette = PaletteResolver.cached(projectDir).load(paletteName);
        } catch (ParseException e) {
            err.println("palette '" + paletteName + "' has errors:");
            e.errors().forEach(x -> err.println("  " + x));
            return 1;
        }

        if (name == null) name = spriteNameFor(png);
        PngImporter.Result r;
        try {
            r = scale == null ? PngImporter.importImage(img, palette) : PngImporter.importImage(img, palette, scale);
        } catch (IllegalArgumentException e) {
            err.println(e.getMessage());
            return 1;
        }
        if (r.grid().width() > SpriteParser.MAX_SIZE || r.grid().height() > SpriteParser.MAX_SIZE) {
            err.println("image is " + r.grid().width() + "x" + r.grid().height() + " after scaling; sprites are at most "
                    + SpriteParser.MAX_SIZE + "x" + SpriteParser.MAX_SIZE);
            return 1;
        }

        String text = PngImporter.toSpriteText(name, paletteName, frame, r.grid(), png.getFileName().toString());
        try {  // the generated text must always be a valid sprite
            SpriteParser.parse(text, "import", name, n -> palette);
        } catch (ParseException e) {
            err.println("internal error, generated sprite is invalid: " + e.getMessage());
            return 1;
        }

        PrintStream report = outFile == null ? err : out;
        if (outFile == null) {
            out.print(text);
        } else {
            Path target = projectDir.resolve(outFile);
            if (Files.exists(target) && !force) {
                err.println(outFile + " already exists; add --force to overwrite it");
                return 1;
            }
            if (target.getParent() != null) Files.createDirectories(target.getParent());
            Files.writeString(target, text, StandardCharsets.UTF_8);
            out.println("wrote " + SpriteFiles.display(projectDir, target));
        }
        printReport(report, img, r);
        return 0;
    }

    private static void printReport(PrintStream p, BufferedImage img, PngImporter.Result r) {
        p.printf("image %dx%d -> %dx%d (scale %d)%n", img.getWidth(), img.getHeight(),
                r.grid().width(), r.grid().height(), r.scale());
        p.printf("colours: %d exact, %d replaced%n", r.exactColours(), r.mismatches().size());
        for (PngImporter.Mismatch m : r.mismatches()) {
            String target = m.symbol() == '.' ? "(no pixel)" : Colors.toHex(m.paletteRgb(), m.paletteAlpha());
            p.printf(Locale.ROOT, "  %s (%d px) -> '%c' %s  distance %.3f%s%n", Colors.toHex(m.rgb(), m.alpha()),
                    m.pixels(), m.symbol(), target, m.distance(), m.poor() ? "  POOR MATCH" : "");
        }
        if (r.semiTransparent() > 0) {
            p.println(r.semiTransparent() + " semi-transparent pixel(s) counted as opaque");
        }
    }

    /** File name without extension, reduced to the characters sprite names allow. */
    static String spriteNameFor(Path png) {
        String base = png.getFileName().toString().replaceFirst("\\.[^.]*$", "");
        String cleaned = base.replaceAll("[^A-Za-z0-9_-]+", "_");
        // A name of only separators ("_", "-_") says nothing, so fall back to a generic one.
        return cleaned.replaceAll("[_-]", "").isEmpty() ? "imported" : cleaned;
    }

    private static int usageError(PrintStream err, String message) {
        err.println(message);
        return 2;
    }
}
