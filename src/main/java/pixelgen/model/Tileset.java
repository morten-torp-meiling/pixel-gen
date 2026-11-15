package pixelgen.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A parsed {@code .tileset} file: a set of equally sized tiles for a Godot TileMapLayer.
 * <p>
 * The tiles are held as the frames of a {@link Sprite}, so everything that draws sprites (render,
 * review, sheet packing) draws tilesets too.
 *
 * @param sprite  the tiles, as frames in file order; the sprite's name is the tileset's
 * @param columns tiles per row in the atlas image
 * @param wraps   how each tile repeats, by tile name; see {@link Wrap}
 */
public record Tileset(Sprite sprite, int columns, Map<String, Wrap> wraps) {

    /** Tiles per atlas row when the file doesn't say. */
    public static final int DEFAULT_COLUMNS = 8;

    /**
     * Which way a tile must continue into a copy of itself. A plain grass tile repeats both ways;
     * the top edge of a path repeats only sideways ({@code x}); a corner doesn't repeat at all.
     */
    public enum Wrap {
        BOTH, X, Y, NONE;

        /** Left and right edges must meet. */
        public boolean x() { return this == BOTH || this == X; }

        /** Top and bottom edges must meet. */
        public boolean y() { return this == BOTH || this == Y; }

        public String word() { return name().toLowerCase(java.util.Locale.ROOT); }
    }

    public Tileset {
        wraps = Map.copyOf(wraps);
    }

    public String name() { return sprite.name(); }

    public int tileWidth() { return sprite.width(); }

    public int tileHeight() { return sprite.height(); }

    public List<String> tiles() { return List.copyOf(sprite.frames().keySet()); }

    public Wrap wrap(String tile) { return wraps.getOrDefault(tile, Wrap.BOTH); }

    /** This tileset followed by each recoloured variant, with the same tiles and layout. */
    public List<Tileset> withVariants() {
        List<Tileset> all = new ArrayList<>();
        for (Sprite s : sprite.withVariants()) all.add(new Tileset(s, columns, wraps));
        return all;
    }
}
