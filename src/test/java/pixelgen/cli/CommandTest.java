package pixelgen.cli;

import org.junit.jupiter.api.io.TempDir;
import pixelgen.Cli;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;

/**
 * What the command tests share: a temporary project folder, files copied or written into it,
 * and running a command there with its output captured.
 */
abstract class CommandTest {

    static final Path RESOURCES = Path.of("src/test/resources");

    @TempDir Path project;

    private final ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errBuf = new ByteArrayOutputStream();

    /** The command {@link #run(String...)} runs. */
    Cli.Command command() {
        throw new UnsupportedOperationException("this test passes its command to run(command, args)");
    }

    /** Runs {@link #command()}; its output replaces the last run's. Returns the exit code. */
    int run(String... args) throws Exception {
        return run(command(), args);
    }

    /** Runs {@code command}; its output replaces the last run's. Returns the exit code. */
    int run(Cli.Command command, String... args) throws Exception {
        outBuf.reset();
        errBuf.reset();
        return Cli.run(command, Arrays.asList(args), new PrintStream(outBuf, true, StandardCharsets.UTF_8),
                new PrintStream(errBuf, true, StandardCharsets.UTF_8));
    }

    String out() { return outBuf.toString(StandardCharsets.UTF_8); }

    String err() { return errBuf.toString(StandardCharsets.UTF_8); }

    /** Copies {@code src/test/resources/<resource>} to {@code target}, creating its folder. */
    static void copy(String resource, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        Files.copy(RESOURCES.resolve(resource), target);
    }

    /** Copies {@code src/test/resources/<resource>} into the project as {@code target}. */
    void copy(String resource, String target) throws IOException {
        copy(resource, project.resolve(target));
    }

    /** Writes {@code text} into the project as {@code target}, creating its folder. */
    void write(String target, String text) throws IOException {
        Path file = project.resolve(target);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }

    /** Adds {@code text} to the end of {@code file}. */
    static void append(Path file, String text) throws IOException {
        Files.writeString(file, text, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
    }

    /** The lines of {@code text}, whatever line endings it has. */
    static List<String> lines(String text) { return text.lines().toList(); }
}
