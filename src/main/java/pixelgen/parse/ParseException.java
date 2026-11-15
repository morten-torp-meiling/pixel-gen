package pixelgen.parse;

import java.io.Serial;
import java.util.List;

/**
 * Thrown when a source file has errors. It carries every error found, not just the first,
 * each already formatted as {@code file:line: message}.
 */
public class ParseException extends Exception {

    @Serial
    private static final long serialVersionUID = 1L;

    private final transient List<String> errors;

    public ParseException(List<String> errors) {
        super(errors.size() == 1
                ? errors.getFirst()
                : errors.size() + " errors:\n  " + String.join("\n  ", errors));
        this.errors = List.copyOf(errors);
    }

    public List<String> errors() { return errors; }
}
