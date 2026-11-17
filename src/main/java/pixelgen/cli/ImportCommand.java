package pixelgen.cli;

import pixelgen.Cli;
import pixelgen.model.Colors;
import pixelgen.model.Palette;
import pixelgen.parse.PaletteResolver;
import pixelgen.parse.ParseException;
import pixelgen.parse.PngImporter;
import pixelgen.parse.ProjectFiles;
import pixelgen.parse.SpriteParser;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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

    public int run(List<String> args, PrintStream out, PrintStream err) throws Exception {
        Options o = Options.parse(args, Set.of("--force"), Set.of("--palette", "--name", "--frame", "--out", "--scale"));
        List<String> positional = o.words();
        String paletteName = o.get("--palette", null), name = o.get("--name", null);
        String frame = o.get("--frame", "idle"), outFile = o.get("--out", null);
        boolean force = o.has("--force");
        if (positional.size() != 1 || paletteName == null) throw new Cli.UsageException("expected: " + usage());
        int scale = o.number("--scale", 0, 1, Integer.MAX_VALUE);  // 0: detected from the image
        if (name != null && !SpriteParser.isName(name)) {
            throw new Cli.UsageException("--name '" + name + "' isn't a sprite name; use letters, digits, _ and -");
        }
        if (!SpriteParser.isName(frame)) {
            throw new Cli.UsageException("--frame '" + frame + "' isn't a frame name; use letters, digits, _ and -");
        }

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
            r = scale == 0 ? PngImporter.importImage(img, palette) : PngImporter.importImage(img, palette, scale);
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
            out.println("wrote " + ProjectFiles.display(projectDir, target));
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
}
