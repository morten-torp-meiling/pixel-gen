package pixelgen.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * An immutable rectangle of palette symbols. {@code '.'} ({@link Palette#TRANSPARENT}) means no pixel.
 * Coordinates are zero-based, with x going right and y going down.
 */
public final class Grid {

    private final int width;
    private final int height;
    private final char[] cells;

    /** Builds a grid from rows of equal length. Throws IllegalArgumentException otherwise. */
    public Grid(List<String> rows) {
        if (rows.isEmpty() || rows.getFirst().isEmpty()) {
            throw new IllegalArgumentException("a grid needs at least one row and one column");
        }
        this.width = rows.getFirst().length();
        this.height = rows.size();
        this.cells = new char[width * height];
        for (int y = 0; y < height; y++) {
            String row = rows.get(y);
            if (row.length() != width) {
                throw new IllegalArgumentException("row " + y + " is " + row.length() + " wide, expected " + width);
            }
            row.getChars(0, width, cells, y * width);
        }
    }

    public static Grid of(String... rows) { return new Grid(List.of(rows)); }

    /** A fully transparent grid. */
    public static Grid blank(int width, int height) {
        return new Grid(java.util.Collections.nCopies(height, String.valueOf(Palette.TRANSPARENT).repeat(width)));
    }

    /** Wraps {@code cells} (row by row) without copying; for code in this package that builds grids. */
    Grid(int width, int height, char[] cells) {
        this.width = width;
        this.height = height;
        this.cells = cells;
    }

    /**
     * Paints {@code part} with its top-left corner at (x, y). Transparent cells of the part leave
     * this grid unchanged; anything outside this grid is cut off.
     */
    public Grid stamp(Grid part, int x, int y) {
        char[] out = cells.clone();
        for (int py = 0; py < part.height; py++) {
            int ty = y + py;
            if (ty < 0 || ty >= height) continue;
            for (int px = 0; px < part.width; px++) {
                int tx = x + px;
                char c = part.cells[py * part.width + px];
                if (tx < 0 || tx >= width || c == Palette.TRANSPARENT) continue;
                out[ty * width + tx] = c;
            }
        }
        return new Grid(width, height, out);
    }

    /** True when placing {@code part} at (x, y) leaves some of its visible cells outside this grid. */
    public boolean clips(Grid part, int x, int y) {
        for (int py = 0; py < part.height; py++) {
            for (int px = 0; px < part.width; px++) {
                if (part.cells[py * part.width + px] == Palette.TRANSPARENT) continue;
                int tx = x + px, ty = y + py;
                if (tx < 0 || ty < 0 || tx >= width || ty >= height) return true;
            }
        }
        return false;
    }

    /** Moved by (dx, dy), right and down for positive values; what leaves the grid is cut off. */
    public Grid shift(int dx, int dy) {
        return blank(width, height).stamp(this, dx, dy);
    }

    /** True when {@link #shift} would cut off visible cells. */
    public boolean shiftClips(int dx, int dy) {
        return blank(width, height).clips(this, dx, dy);
    }

    /** Mirrored left to right. */
    public Grid flipX() {
        char[] out = new char[cells.length];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) out[y * width + x] = cells[y * width + (width - 1 - x)];
        }
        return new Grid(width, height, out);
    }

    /** Mirrored top to bottom. */
    public Grid flipY() {
        char[] out = new char[cells.length];
        for (int y = 0; y < height; y++) {
            System.arraycopy(cells, (height - 1 - y) * width, out, y * width, width);
        }
        return new Grid(width, height, out);
    }

    /**
     * Replaces symbols all at once, so {@code a>b b>a} swaps them. Symbols not in the map are kept.
     */
    public Grid recolor(java.util.Map<Character, Character> map) {
        char[] out = cells.clone();
        for (int i = 0; i < out.length; i++) {
            Character to = map.get(out[i]);
            if (to != null) out[i] = to;
        }
        return new Grid(width, height, out);
    }

    /** A copy of the cells, row by row. */
    char[] cells() { return cells.clone(); }

    public int width() { return width; }

    public int height() { return height; }

    public char at(int x, int y) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            throw new IndexOutOfBoundsException("(" + x + "," + y + ") is outside " + width + "x" + height);
        }
        return cells[y * width + x];
    }

    /** True when every cell is transparent. */
    public boolean isBlank() {
        for (char c : cells) if (c != Palette.TRANSPARENT) return false;
        return true;
    }

    public List<String> rows() {
        List<String> rows = new ArrayList<>(height);
        for (int y = 0; y < height; y++) rows.add(new String(cells, y * width, width));
        return rows;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Grid g && g.width == width && g.height == height && Arrays.equals(g.cells, cells);
    }

    @Override
    public int hashCode() { return 31 * width + Arrays.hashCode(cells); }

    @Override
    public String toString() { return String.join("\n", rows()); }
}
