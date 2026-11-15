package pixelgen.render;

import pixelgen.model.Anim;
import pixelgen.model.Sprite;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Packs a sprite's frames into one sheet (texture atlas) at 1:1, for the game.
 * <ul>
 *   <li>One row per animation, holding the frames it introduces in play order. A frame used by
 *       several animations is stored once, in the row of the first animation that uses it.</li>
 *   <li>Frames no animation uses go in a last row, so still sprites get a sheet too.</li>
 *   <li>Every cell is the frame size. {@code padding} transparent pixels go between cells and
 *       around the edge. {@code extrude} repeats each frame's edge pixels outward, so a GPU
 *       sampling just past a frame's edge (filtering, mipmaps, sub-pixel camera moves) picks up the
 *       frame's own colour rather than a neighbour's. Tiles need it; sprites with a transparent
 *       border rarely do.</li>
 * </ul>
 */
public final class SheetPacker {

    public static final int MAX_PADDING = 16;
    public static final int MAX_SIDE = 16384;  // Godot's texture size limit

    private SheetPacker() {}

    /** A row of the sheet: the animation that introduced it (or null for unused frames) and its frames. */
    public record Row(String anim, List<String> frames) {}

    /**
     * Where everything sits. {@code frames} maps each frame name to its region: the frame's own
     * pixels, not counting padding or extrusion.
     */
    public record Layout(int width, int height, int padding, int extrude, List<Row> rows,
                         Map<String, Rectangle> frames) {}

    public static Layout layout(Sprite sprite, int padding, int extrude) {
        checkSpacing(padding, extrude);
        List<Row> rows = new ArrayList<>();
        Set<String> placed = new LinkedHashSet<>();
        for (Anim a : sprite.anims().values()) {
            List<String> row = new ArrayList<>();
            for (Anim.Step step : a.steps()) {
                if (placed.add(step.frame())) row.add(step.frame());
            }
            if (!row.isEmpty()) rows.add(new Row(a.name(), row));
        }
        List<String> rest = new ArrayList<>();
        for (String f : sprite.frames().keySet()) if (!placed.contains(f)) rest.add(f);
        if (!rest.isEmpty()) rows.add(new Row(null, rest));
        return place(sprite, rows, padding, extrude);
    }

    /**
     * A plain grid for tile atlases: every frame in file order, {@code columns} per row, so a
     * tile's atlas coordinates are its column and row.
     */
    public static Layout grid(Sprite sprite, int columns, int padding, int extrude) {
        checkSpacing(padding, extrude);
        if (columns < 1) throw new IllegalArgumentException("columns must be at least 1");
        List<String> names = new ArrayList<>(sprite.frames().keySet());
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < names.size(); i += columns) {
            rows.add(new Row(null, List.copyOf(names.subList(i, Math.min(names.size(), i + columns)))));
        }
        return place(sprite, rows, padding, extrude);
    }

    private static void checkSpacing(int padding, int extrude) {
        if (padding < 0 || padding > MAX_PADDING) {
            throw new IllegalArgumentException("padding must be between 0 and " + MAX_PADDING);
        }
        if (extrude < 0 || extrude * 2 > padding) {
            throw new IllegalArgumentException("extrude must be between 0 and half the padding, so neighbours"
                    + " don't overlap (padding " + padding + " allows up to " + padding / 2 + ")");
        }
    }

    private static Layout place(Sprite sprite, List<Row> rows, int padding, int extrude) {
        // Extrusion lives inside the padding: each side of a gap gets at most half of it.
        int stepX = sprite.width() + padding, stepY = sprite.height() + padding;
        Map<String, Rectangle> frames = new LinkedHashMap<>();
        int columns = 0;
        for (int r = 0; r < rows.size(); r++) {
            List<String> names = rows.get(r).frames();
            columns = Math.max(columns, names.size());
            for (int c = 0; c < names.size(); c++) {
                frames.put(names.get(c), new Rectangle(padding + c * stepX, padding + r * stepY,
                        sprite.width(), sprite.height()));
            }
        }
        int width = padding + columns * stepX, height = padding + rows.size() * stepY;
        if (width > MAX_SIDE || height > MAX_SIDE) {
            throw new IllegalArgumentException("the sheet would be " + width + "x" + height + "; Godot's limit is "
                    + MAX_SIDE + " per side");
        }
        return new Layout(width, height, padding, extrude, List.copyOf(rows), frames);
    }

    /** Draws the sheet for a layout made from this sprite (or a variant of it, which has the same frames). */
    public static BufferedImage render(Sprite sprite, Layout layout) {
        BufferedImage sheet = new BufferedImage(layout.width(), layout.height(), BufferedImage.TYPE_INT_ARGB);
        int e = layout.extrude();
        layout.frames().forEach((name, r) -> {
            BufferedImage img = Renderer.render(sprite.frame(name).grid(), sprite.palette());
            // Every sheet pixel in the extruded cell copies the nearest pixel of the frame.
            for (int y = -e; y < r.height + e; y++) {
                for (int x = -e; x < r.width + e; x++) {
                    int sx = Math.clamp(x, 0, r.width - 1), sy = Math.clamp(y, 0, r.height - 1);
                    sheet.setRGB(r.x + x, r.y + y, img.getRGB(sx, sy));
                }
            }
        });
        return sheet;
    }
}
