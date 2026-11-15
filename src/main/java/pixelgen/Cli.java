package pixelgen;

import pixelgen.cli.PaletteCommand;
import pixelgen.cli.RenderCommand;
import pixelgen.cli.ValidateCommand;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Entry point for the pixel-gen toolchain: {@code gradlew run --args="<command> [args...]"}.
 * Each later step registers its subcommand in {@link #COMMANDS}.
 */
public final class Cli {

    public static final String VERSION = "0.1.0";

    /** A subcommand. Returns the process exit code. */
    public interface Command {
        String usage();
        String summary();
        int run(List<String> args, PrintStream out, PrintStream err) throws Exception;
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
        COMMANDS.put("validate", new ValidateCommand(projectDir));
        COMMANDS.put("render", new RenderCommand(projectDir));
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
            return cmd.run(argv.subList(1, argv.size()), out, err);
        } catch (Exception e) {
            err.println("Error: " + (e.getMessage() != null ? e.getMessage() : e.toString()));
            return 1;
        }
    }

    private static void printHelp(PrintStream out) {
        out.println("pixel-gen " + VERSION + " - text-grid pixel art toolchain");
        out.println();
        out.println("Usage: gradlew run -q --args=\"<command> [args...]\"");
        out.println();
        out.println("Commands:");
        int width = COMMANDS.values().stream().mapToInt(c -> c.usage().length()).max().orElse(0);
        for (Command c : COMMANDS.values()) {
            out.printf("  %-" + width + "s   %s%n", c.usage(), c.summary());
        }
    }
}
