package pixelgen.model;

/**
 * Post-process effects that draw on a finished frame: an automatic outline and a soft shadow.
 * Both only paint where there is no solid pixel yet, so they never cover the art itself.
 */
public final class Effects {

    private Effects() {}

    /** How an outline is coloured. */
    public enum OutlineMode {
        /** Every outline pixel in the given colour. */
        OUTER,
        /**
         * "Sel-out": on the lit top and left sides the outline takes the darkest shade of the
         * colour it borders (so a leaf gets a dark green edge), elsewhere the given colour. This
         * keeps black off the lit edges, matching light from the top left.
         */
        SELECTIVE
    }

    /** An effect's image, and whether some of it fell outside the frame. */
    public record Result(Grid grid, boolean clipped) {}

    /**
     * Adds a 1 px outline around every solid (opaque) shape, on its four sides (not diagonally,
     * which keeps corners round). Cells that are empty or hold a translucent colour, such as a
     * shadow, can become outline; opaque cells never change.
     */
    public static Result outline(Grid grid, Palette palette, char symbol, OutlineMode mode) {
        int w = grid.width(), h = grid.height();
        char[] in = grid.cells();
        char[] out = in.clone();
        boolean clipped = false;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                char c = in[y * w + x];
                if (palette.isOpaque(c)) {
                    if (x == 0 || y == 0 || x == w - 1 || y == h - 1) clipped = true;
                    continue;
                }
                // Neighbours below and to the right mean this cell is on the shape's top or left side.
                char below = solid(in, w, h, x, y + 1, palette);
                char right = solid(in, w, h, x + 1, y, palette);
                char above = solid(in, w, h, x, y - 1, palette);
                char left = solid(in, w, h, x - 1, y, palette);
                if (below == 0 && right == 0 && above == 0 && left == 0) continue;
                char colour = symbol;
                if (mode == OutlineMode.SELECTIVE && (below != 0 || right != 0)) {
                    colour = darkestShade(below != 0 ? below : right, symbol, palette);
                }
                out[y * w + x] = colour;
            }
        }
        return new Result(new Grid(w, h, out), clipped);
    }

    /**
     * Draws a filled ellipse {@code width} × {@code height} behind the art, centred on the anchor
     * column with its bottom row on the anchor row (where the feet touch the ground). Only empty
     * cells are painted.
     */
    public static Result shadow(Grid grid, char symbol, int width, int height, int anchorX, int anchorY) {
        if (width < 1 || height < 1) throw new IllegalArgumentException("shadow size must be at least 1x1");
        int w = grid.width(), h = grid.height();
        char[] out = grid.cells();
        boolean clipped = false;
        // Centre in pixel-edge coordinates: pixel i covers [i, i+1). An even width straddles the
        // anchor column's left edge, an odd width is centred on the anchor column.
        double cx = anchorX + (width % 2) * 0.5;
        double cy = anchorY + 1 - height / 2.0;
        double rx = width / 2.0, ry = height / 2.0;
        int top = anchorY + 1 - height, left = (int) Math.floor(cx - rx);
        for (int y = top; y <= anchorY; y++) {
            for (int x = left; x < left + width; x++) {
                double dx = (x + 0.5 - cx) / rx, dy = (y + 0.5 - cy) / ry;
                if (dx * dx + dy * dy > 1.0 + 1e-9) continue;
                if (x < 0 || y < 0 || x >= w || y >= h) {
                    clipped = true;
                    continue;
                }
                if (out[y * w + x] == Palette.TRANSPARENT) out[y * w + x] = symbol;
            }
        }
        return new Result(new Grid(w, h, out), clipped);
    }

    /** The opaque symbol at (x, y), or 0 when it's outside the grid, empty or translucent. */
    private static char solid(char[] cells, int w, int h, int x, int y, Palette palette) {
        if (x < 0 || y < 0 || x >= w || y >= h) return 0;
        char c = cells[y * w + x];
        return palette.isOpaque(c) ? c : 0;
    }

    /**
     * The darkest colour of {@code neighbour}'s ramp, or {@code fallback} when the ramp isn't
     * ordered or the neighbour already is that darkest shade (an edge in the same colour
     * wouldn't show).
     */
    private static char darkestShade(char neighbour, char fallback, Palette palette) {
        Palette.Entry e = palette.get(neighbour);
        Palette.Ramp ramp = palette.ramp(e.ramp());
        if (ramp == null || !ramp.ordered()) return fallback;
        char darkest = ramp.entries().getFirst().symbol();
        return darkest == neighbour ? fallback : darkest;
    }
}
