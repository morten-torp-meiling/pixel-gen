package pixelgen.cli;

import pixelgen.Cli;
import pixelgen.model.Effects;
import pixelgen.model.Style;
import pixelgen.parse.PaletteResolver;
import pixelgen.parse.ParseException;
import pixelgen.parse.StyleParser;
import pixelgen.parse.Styles;

import java.io.PrintStream;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;

/**
 * {@code style <name|file.style>} checks a style and prints its settings; {@code style} alone
 * lists the project's styles and names the default one.
 */
public final class StyleCommand implements Cli.Command {

    private final Path projectDir;

    public StyleCommand(Path projectDir) { this.projectDir = projectDir; }

    public String usage() { return "style [<name>]"; }

    public String summary() { return "Check a style and show its settings, or list the styles"; }

    public int run(List<String> args, PrintStream out, PrintStream err) throws Exception {
        if (args.size() > 1 || (args.size() == 1 && args.getFirst().startsWith("-"))) {
            err.println("expected: style [<name|file.style>]");
            return 2;
        }
        PaletteResolver resolver = PaletteResolver.cached(projectDir);
        String defaultName;
        try {
            defaultName = Styles.defaultName(projectDir);
        } catch (ParseException e) {
            for (String m : e.errors()) err.println(m);
            return 1;
        }
        return args.isEmpty() ? list(resolver, defaultName, out, err)
                : show(args.getFirst(), resolver, defaultName, out, err);
    }

    private int show(String nameOrPath, PaletteResolver resolver, String defaultName, PrintStream out,
                     PrintStream err) throws Exception {
        Style s;
        try {
            s = resolver.style(nameOrPath);
        } catch (NoSuchFileException e) {
            err.println("style '" + nameOrPath + "' not found (" + e.getFile() + ")");
            return 1;
        } catch (ParseException e) {
            for (String m : e.errors()) err.println(m);
            return 1;
        }
        out.println("style " + s.name() + (s.name().equals(defaultName) ? " (the project default)" : ""));
        out.println("  palette  " + s.palette().name() + ", " + s.palette().size() + " colours");
        out.println("  light    " + s.light().word());
        out.println("  outline  " + outline(s));
        out.println("  shadow   " + (s.hasShadow() ? String.valueOf(s.shadow()) : "none"));
        out.println("  tile     " + s.tileWidth() + "x" + s.tileHeight());
        out.println("  guide    " + relative(s.guide()));
        for (String w : s.warnings()) err.println("warning: " + w);
        return 0;
    }

    private int list(PaletteResolver resolver, String defaultName, PrintStream out, PrintStream err)
            throws Exception {
        out.println(defaultName != null
                ? "default style: " + defaultName + " (" + Styles.CONF + ")"
                : "default style: built-in (no " + Styles.CONF + "): files without a 'style' line name their palette"
                        + " and are lit from the top left");
        List<Path> files = Styles.list(projectDir);
        if (files.isEmpty()) out.println("no styles in " + Styles.FOLDER + "/");
        int failed = 0;
        boolean defaultFound = defaultName == null;
        for (Path f : files) {
            String file = f.getFileName().toString();
            String name = file.substring(0, file.length() - StyleParser.EXTENSION.length());
            defaultFound |= name.equals(defaultName);
            try {
                Style s = resolver.style(Styles.FOLDER + "/" + file);
                out.printf("  %-12s palette %s, light %s, outline %s, shadow %s, tile %dx%d%s%n", name,
                        s.palette().name(), s.light().word(), outline(s), s.hasShadow() ? s.shadow() : "none",
                        s.tileWidth(), s.tileHeight(), name.equals(defaultName) ? "  (default)" : "");
                for (String w : s.warnings()) err.println("warning: " + w);
            } catch (ParseException e) {
                failed++;
                for (String m : e.errors()) err.println(m);
            }
        }
        if (!defaultFound) {
            err.println(Styles.CONF + ": the default style '" + defaultName + "' has no file "
                    + Styles.FOLDER + "/" + defaultName + StyleParser.EXTENSION);
            failed++;
        }
        return failed == 0 ? 0 : 1;
    }

    private static String outline(Style s) {
        if (!s.hasOutline()) return "none";
        return s.outline() + (s.outlineMode() == Effects.OutlineMode.SELECTIVE ? " selective" : " outer");
    }

    private String relative(Path p) {
        Path dir = projectDir.toAbsolutePath().normalize();
        return p.startsWith(dir) ? dir.relativize(p).toString().replace('\\', '/') : p.toString();
    }
}
