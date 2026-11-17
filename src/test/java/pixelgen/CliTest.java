package pixelgen;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CliTest {

    private final ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errBuf = new ByteArrayOutputStream();

    private int run(String... args) {
        return Cli.run(List.of(args),
                new PrintStream(outBuf, true, StandardCharsets.UTF_8),
                new PrintStream(errBuf, true, StandardCharsets.UTF_8));
    }

    private String out() { return outBuf.toString(StandardCharsets.UTF_8); }
    private String err() { return errBuf.toString(StandardCharsets.UTF_8); }

    @Test
    void noArgumentsPrintsHelp() {
        assertEquals(0, run());
        assertTrue(out().contains("Usage: pixel-gen <command>"), out());
        assertTrue(out().contains("version"), out());
    }

    @Test
    void versionPrintsVersion() {
        assertEquals(0, run("version"));
        assertEquals("pixel-gen " + Cli.VERSION, out().strip());
    }

    @Test
    void unknownCommandIsAUsageError() {
        assertEquals(2, run("bogus"));
        assertTrue(err().contains("Unknown command: bogus"), err());
    }
}
