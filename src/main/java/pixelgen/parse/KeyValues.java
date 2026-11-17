package pixelgen.parse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads settings files made of {@code key = value} lines ({@code pixelgen.conf},
 * {@code styles/styles.conf}). Blank lines and {@code #} comments are skipped; a line without
 * {@code =} and a key set twice are errors.
 */
public final class KeyValues {

    private KeyValues() {}

    /** Receives each setting in file order. {@code where} is the {@code source:line: } prefix. */
    @FunctionalInterface
    public interface Setting {
        void accept(String key, String value, int line, String where);
    }

    /** Calls {@code setting} for each well-formed line; adds an error for every other one. */
    public static void read(String text, String source, List<String> errors, Setting setting) {
        Map<String, Integer> seen = new HashMap<>();
        String[] lines = text.split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String where = source + ":" + (i + 1) + ": ";
            int eq = line.indexOf('=');
            if (eq < 0) {
                errors.add(where + "expected 'key = value'");
                continue;
            }
            String key = line.substring(0, eq).strip(), value = line.substring(eq + 1).strip();
            Integer first = seen.putIfAbsent(key, i + 1);
            if (first != null) {
                errors.add(where + "'" + key + "' is already set on line " + first);
                continue;
            }
            setting.accept(key, value, i + 1, where);
        }
    }
}
