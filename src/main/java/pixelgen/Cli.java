package pixelgen;

import pixelgen.cli.ExportCommand;
import pixelgen.cli.FramesCommand;
import pixelgen.cli.GifCommand;
import pixelgen.cli.ImportCommand;
import pixelgen.cli.MockupCommand;
import pixelgen.cli.PaletteCommand;
import pixelgen.cli.RenderCommand;
import pixelgen.cli.SheetCommand;
import pixelgen.cli.StyleCommand;
import pixelgen.cli.TilecheckCommand;
import pixelgen.cli.TilesCommand;
import pixelgen.cli.ReviewCommand;
import pixelgen.cli.ValidateCommand;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Entry point for the pixel-gen toolchain: {@code pixel-gen <command> [args...]}, with the
 * launcher {@code gradlew installDist} makes. Every command is registered in {@link #COMMANDS}.
 */
public final class Cli {

    /** The version from build.gradle.kts (via the jar manifest); "dev" when run from classes. */
    public static final String VERSION = java.util.Objects.requireNonNullElse(
            Cli.class.getPackage().getImplementationVersion(), "dev");

    /**
     * A subcommand. Returns the process exit code; throws {@link UsageException} for a wrong
     * command line (see {@link #run(Command, List, PrintStream, PrintStream)}).
     */
    public interface Command {
        String usage();
        String summary();
        int run(List<String> args, PrintStream out, PrintStream err) throws Exception;
    }

    /** A wrong command line. Its message says what's wrong; the exit code is 2. */
    public static final class UsageException extends Exception {
        @java.io.Serial
        private static final long serialVersionUID = 1L;

        public UsageException(String message) { super(message); }
    }

    private static final Map<String, Command> COMMANDS = new LinkedHashMap<>();

    static {
        COMMANDS.put("help", new Command() {
            public String usage() { return "help"; }
            public String summary() { return "Show this list of commands"; }
            public int run(List<String> args, PrintStream out, PrintStream err) {
                printHelp(out);
                return 0;
            }
        });
        COMMANDS.put("version", new Command() {
            public String usage() { return "version"; }
            public String summary() { return "Print the pixel-gen version"; }
            public int run(List<String> args, PrintStream out, PrintStream err) {
                out.println("pixel-gen " + VERSION);
                return 0;
            }
        });
        // Commands resolve relative paths (palettes/, assets/, preview/) against the current folder.
        Path projectDir = Path.of("").toAbsolutePath();
        COMMANDS.put("palette", new PaletteCommand(projectDir));
        COMMANDS.put("style", new StyleCommand(projectDir));
        COMMANDS.put("validate", new ValidateCommand(projectDir));
        COMMANDS.put("render", new RenderCommand(projectDir));
        COMMANDS.put("review", new ReviewCommand(projectDir));
        COMMANDS.put("gif", new GifCommand(projectDir));
        COMMANDS.put("sheet", new SheetCommand(projectDir));
        COMMANDS.put("frames", new FramesCommand(projectDir));
        COMMANDS.put("tilecheck", new TilecheckCommand(projectDir));
        COMMANDS.put("tiles", new TilesCommand(projectDir));
        COMMANDS.put("mockup", new MockupCommand(projectDir));
        COMMANDS.put("export", new ExportCommand(projectDir));
        COMMANDS.put("import", new ImportCommand(projectDir));
    }

    private Cli() {}

    public static void main(String[] argv) {
        System.exit(run(List.of(argv), System.out, System.err));
    }

    /** Runs a command line and returns the exit code (0 = ok, 1 = command failed, 2 = usage error). */
    public static int run(List<String> argv, PrintStream out, PrintStream err) {
        if (argv.isEmpty() || argv.getFirst().equals("--help") || argv.getFirst().equals("-h")) {
            printHelp(out);
            return 0;
        }
        String name = argv.getFirst();
        Command cmd = COMMANDS.get(name);
        if (cmd == null) {
            err.println("Unknown command: " + name);
            err.println("Run 'help' to see the available commands.");
            return 2;
        }
        try {
            return run(cmd, argv.subList(1, argv.size()), out, err);
        } catch (Exception e) {
            err.println("Error: " + (e.getMessage() != null ? e.getMessage() : e.toString()));
            return 1;
        }
    }

    /** Runs one command; a {@link UsageException} is printed and gives exit code 2. */
    public static int run(Command cmd, List<String> args, PrintStream out, PrintStream err) throws Exception {
        try {
            return cmd.run(args, out, err);
        } catch (UsageException e) {
            err.println(e.getMessage());
            return 2;
        }
    }

    private static void printHelp(PrintStream out) {
        out.println("pixel-gen " + VERSION + " - text-grid pixel art toolchain");
        out.println();
        out.println("Usage: pixel-gen <command> [args...]");
        out.println();
        out.println("Commands:");
        int width = COMMANDS.values().stream().mapToInt(c -> c.usage().length()).max().orElse(0);
        for (Command c : COMMANDS.values()) {
            out.printf("  %-" + width + "s   %s%n", c.usage(), c.summary());
        }
    }
}
