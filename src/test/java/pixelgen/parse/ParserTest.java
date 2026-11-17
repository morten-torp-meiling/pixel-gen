package pixelgen.parse;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** What the parser tests share: the test resources, and checks on the errors a text gives. */
abstract class ParserTest {

    static final Path RESOURCES = Path.of("src/test/resources");

    /** Gives every palette name the test palette {@code name}, such as {@code basic} or {@code fx}. */
    static PaletteResolver only(String name) {
        return n -> Palettes.load(name, RESOURCES);
    }

    /** Parses a test text the way this test's parser does. */
    abstract Object parse(String text) throws Exception;

    /** The errors {@code text} gives; fails when it parses. */
    List<String> errorsOf(String text) {
        return assertThrows(ParseException.class, () -> parse(text)).errors();
    }

    /** Checks that {@code text} gives exactly one error, and that it contains {@code part}. */
    void assertOneError(String text, String part) {
        List<String> errors = errorsOf(text);
        assertEquals(1, errors.size(), errors.toString());
        assertTrue(errors.getFirst().contains(part), errors.getFirst());
    }
}
