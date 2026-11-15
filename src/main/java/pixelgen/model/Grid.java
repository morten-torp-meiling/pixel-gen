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
