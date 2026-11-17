package pixelgen.model;

import java.nio.file.Path;
import java.util.List;

/**
 * A named look for a group of sprites and tilesets, read from {@code styles/<name>.style}: the
 * palette, where the light comes from, what a bare {@code outline} or {@code shadow} line draws,
 * the tile size, and the guide that holds the style's own drawing rules.
 *
 * @param name        the style's name
 * @param palette     the palette its files use unless they name another; null for {@link #BUILT_IN}
 * @param light       where the light comes from
 * @param outline     colour of a bare {@code outline} line, or 0 when the style sets none
 * @param outlineMode mode of a bare {@code outline} line (ignored when {@code outline} is 0)
 * @param shadow      colour of a {@code shadow <w> <h>} line, or 0 when the style sets none
 * @param tileWidth   tile width for tilesets that don't set a size
 * @param tileHeight  tile height for tilesets that don't set a size
 * @param guide       the style's guide (a Markdown file), or null for {@link #BUILT_IN}
 * @param warnings    problems worth a look that don't stop the style from being used
 */
public record Style(String name, Palette palette, Light light, char outline, Effects.OutlineMode outlineMode,
                    char shadow, int tileWidth, int tileHeight, Path guide, List<String> warnings) {

    public static final int DEFAULT_TILE_SIZE = 16;

    /**
     * What files get when the project names no default style: no palette (so files must name
     * one), light from the top left, no outline or shadow defaults, 16 × 16 tiles.
     */
    public static final Style BUILT_IN = new Style("built-in", null, Light.TOP_LEFT, (char) 0,
            Effects.OutlineMode.OUTER, (char) 0, DEFAULT_TILE_SIZE, DEFAULT_TILE_SIZE, null, List.of());

    public Style {
        warnings = List.copyOf(warnings);
    }

    public boolean hasOutline() { return outline != 0; }

    public boolean hasShadow() { return shadow != 0; }

    public boolean isBuiltIn() { return this == BUILT_IN; }
}
