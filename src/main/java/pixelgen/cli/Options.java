package pixelgen.cli;

import pixelgen.Cli.UsageException;
import pixelgen.render.Renderer;
import pixelgen.render.SheetPacker;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A command line split into words (files, folders) and {@code --options}: flags such as
 * {@code --strip}, and options with a value such as {@code --scale 4}. Unknown options, a
 * missing value and bad numbers are {@link UsageException}s.
 */
final class Options {

    private final List<String> words = new ArrayList<>();
    private final Set<String> flags = new HashSet<>();
    private final Map<String, String> values = new HashMap<>();

    private Options() {}

    /**
     * @param flags  the options without a value
     * @param valued the options followed by a value
     */
    static Options parse(List<String> args, Set<String> flags, Set<String> valued) throws UsageException {
        Options o = new Options();
        for (int i = 0; i < args.size(); i++) {
            String a = args.get(i);
            if (!a.startsWith("--")) {
                o.words.add(a);
            } else if (flags.contains(a)) {
                o.flags.add(a);
            } else if (!valued.contains(a)) {
                throw new UsageException("unknown option " + a);
            } else if (i + 1 >= args.size()) {
                throw new UsageException(a + " needs a value");
            } else {
                o.values.put(a, args.get(++i));
            }
        }
        return o;
    }

    /** The words, which must number at least one; otherwise "expected: {@code usage}". */
    List<String> targets(String usage) throws UsageException {
        if (words.isEmpty()) throw new UsageException("expected: " + usage);
        return words;
    }

    List<String> words() { return words; }

    /** True when the flag, or the option with a value, was given. */
    boolean has(String option) { return flags.contains(option) || values.containsKey(option); }

    String get(String option, String defaultValue) { return values.getOrDefault(option, defaultValue); }

    /** A whole number from {@code min} to {@code max}, or {@code defaultValue} when not given. */
    int number(String option, int defaultValue, int min, int max) throws UsageException {
        String v = values.get(option);
        if (v == null) return defaultValue;
        int n;
        try {
            n = Integer.parseInt(v);
        } catch (NumberFormatException e) {
            throw new UsageException(option + " needs a whole number, not '" + v + "'");
        }
        if (n < min || n > max) throw new UsageException(option + " must be between " + min + " and " + max);
        return n;
    }

    /** {@code --scale}, from 1 to {@link Renderer#MAX_SCALE}. */
    int scale(int defaultValue) throws UsageException {
        return number("--scale", defaultValue, 1, Renderer.MAX_SCALE);
    }

    /** {@code --out}, resolved against the project folder. */
    Path out(Path projectDir, String defaultFolder) {
        return projectDir.resolve(get("--out", defaultFolder));
    }

    /** Space around sheet cells: {@code --padding} and {@code --extrude}, which fits inside half the padding. */
    record Spacing(int padding, int extrude) {}

    Spacing spacing(int defaultPadding) throws UsageException {
        int padding = number("--padding", defaultPadding, 0, SheetPacker.MAX_PADDING);
        int extrude = number("--extrude", 0, Integer.MIN_VALUE, Integer.MAX_VALUE);
        if (extrude < 0 || extrude > padding / 2) {
            throw new UsageException("--extrude must be between 0 and half the padding (" + padding / 2
                    + "); use for example --padding 2 --extrude 1");
        }
        return new Spacing(padding, extrude);
    }

    /** {@code --res}: a Godot folder such as {@code res://sprites}, returned with a trailing slash. */
    String resDir(String defaultValue) throws UsageException {
        String res = get("--res", defaultValue);
        if (!res.startsWith("res://") || res.contains("\\")) {
            throw new UsageException("--res must be a Godot folder such as " + defaultValue + ", not '" + res + "'");
        }
        return res.endsWith("/") ? res : res + "/";
    }
}
