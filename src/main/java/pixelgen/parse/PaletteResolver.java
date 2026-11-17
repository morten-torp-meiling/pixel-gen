package pixelgen.parse;

import pixelgen.model.Palette;
import pixelgen.model.Style;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads what a sprite's header names: its {@code palette} and its {@code style}. A plain lambda
 * resolves palettes only; it knows no styles and gives {@link Style#BUILT_IN} as the default.
 */
@FunctionalInterface
public interface PaletteResolver {

    Palette load(String nameOrPath) throws IOException, ParseException;

    /** The style a {@code style} line names. */
    default Style style(String nameOrPath) throws IOException, ParseException {
        throw new NoSuchFileException(nameOrPath, null, "style not found (no styles here)");
    }

    /** The style of files without a {@code style} line. */
    default Style defaultStyle() throws IOException, ParseException {
        return Style.BUILT_IN;
    }

    /**
     * Resolves names against {@code projectDir} (see {@link Palettes} and {@link Styles}) and
     * loads each palette and style once.
     */
    static PaletteResolver cached(Path projectDir) {
        return new PaletteResolver() {
            final Map<String, Palette> palettes = new HashMap<>();
            final Map<String, Style> styles = new HashMap<>();
            Style defaultStyle;

            public Palette load(String nameOrPath) throws IOException, ParseException {
                Palette p = palettes.get(nameOrPath);
                if (p == null) {
                    p = Palettes.load(nameOrPath, projectDir);
                    palettes.put(nameOrPath, p);
                }
                return p;
            }

            public Style style(String nameOrPath) throws IOException, ParseException {
                Style s = styles.get(nameOrPath);
                if (s == null) {
                    s = Styles.load(nameOrPath, projectDir, this);
                    styles.put(nameOrPath, s);
                }
                return s;
            }

            public Style defaultStyle() throws IOException, ParseException {
                if (defaultStyle == null) {
                    String name = Styles.defaultName(projectDir);
                    defaultStyle = name == null ? Style.BUILT_IN : style(name);
                }
                return defaultStyle;
            }
        };
    }
}
