package pixelgen.parse;

import pixelgen.model.Palette;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Loads the palette a sprite names in its {@code palette} line. */
@FunctionalInterface
public interface PaletteResolver {

    Palette load(String nameOrPath) throws IOException, ParseException;

    /** Resolves names against {@code projectDir} (see {@link Palettes}) and loads each palette once. */
    static PaletteResolver cached(Path projectDir) {
        Map<String, Palette> cache = new HashMap<>();
        return nameOrPath -> {
            Palette p = cache.get(nameOrPath);
            if (p == null) {
                p = Palettes.load(nameOrPath, projectDir);
                cache.put(nameOrPath, p);
            }
            return p;
        };
    }
}
