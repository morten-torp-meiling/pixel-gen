package pixelgen.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Finds tiles whose edges don't continue into a copy of themselves, which shows as a grid of
 * lines when the tile is painted over a large area.
 * <p>
 * A tile is never perfectly smooth (grass has blades and specks), so an edge isn't compared with
 * zero but with the tile's own texture: across the seam, neighbouring pixels may differ about as
 * much as neighbouring pixels inside the tile do.
 * <ul>
 *   <li><b>inside</b>: the average colour difference between neighbours inside the tile, across
 *       the same direction (columns next to each other, for the left/right seam);</li>
 *   <li><b>seam</b>: the average difference between each edge pixel and the pixel it meets on the
 *       opposite edge;</li>
 *   <li>the seam fails when it's more than {@value #FACTOR} times the inside difference plus
 *       {@value #SLACK} (so a few stray specks don't fail a flat tile).</li>
 * </ul>
 * Differences are OKLab distances (a neighbouring shade in a ramp is about 0.05-0.1); a
 * transparent pixel next to a solid one counts as {@value #EMPTY_VS_SOLID}.
 */
public final class SeamCheck {

    public static final double FACTOR = 2.0;
    public static final double SLACK = 0.02;
    /** A single edge pixel counts as part of the seam when it differs at least this much. */
    public static final double PIXEL_MIN = 0.08;
    public static final double EMPTY_VS_SOLID = 0.5;

    private SeamCheck() {}

    /** Which pair of edges: {@code X} is right edge to left edge, {@code Y} bottom edge to top edge. */
    public enum Axis { X, Y }

    /**
     * The result for one pair of edges.
     *
     * @param positions rows (for X) or columns (for Y) where the edge pixels clearly don't match
     */
    public record Seam(String tile, Axis axis, double seam, double inside, List<Integer> positions, boolean ok) {

        /** Like "the right edge doesn't continue into the left edge at rows 0-3, 9 (seam 0.21, inside 0.04)". */
        public String describe() {
            String edges = axis == Axis.X ? "the right edge doesn't continue into the left edge"
                    : "the bottom edge doesn't continue into the top edge";
            String where = positions.isEmpty() ? ""
                    : " at " + (axis == Axis.X ? "rows " : "columns ") + ranges(positions);
            return String.format(Locale.ROOT, "%s%s (seam %.2f, inside %.2f)", edges, where, seam, inside);
        }
    }

    /** Checks the edges the tile's wrap says must meet, in X then Y order. */
    public static List<Seam> check(Tileset tileset) {
        List<Seam> seams = new ArrayList<>();
        Palette p = tileset.sprite().palette();
        for (String tile : tileset.tiles()) {
            Grid g = tileset.sprite().frame(tile).grid();
            Tileset.Wrap w = tileset.wrap(tile);
            if (w.x()) seams.add(check(tile, g, p, Axis.X));
            if (w.y()) seams.add(check(tile, g, p, Axis.Y));
        }
        return seams;
    }

    public static Seam check(String tile, Grid g, Palette p, Axis axis) {
        // Work in "across" (the direction the seam is crossed) and "along" coordinates.
        int across = axis == Axis.X ? g.width() : g.height();
        int along = axis == Axis.X ? g.height() : g.width();
        double insideSum = 0;
        int insideCount = 0;
        for (int a = 0; a + 1 < across; a++) {
            for (int b = 0; b < along; b++) {
                insideSum += difference(p, cell(g, axis, a, b), cell(g, axis, a + 1, b));
                insideCount++;
            }
        }
        double inside = insideCount == 0 ? 0 : insideSum / insideCount;
        double seamSum = 0;
        List<Integer> positions = new ArrayList<>();
        for (int b = 0; b < along; b++) {
            double d = difference(p, cell(g, axis, across - 1, b), cell(g, axis, 0, b));
            seamSum += d;
            if (d >= PIXEL_MIN && d > FACTOR * inside) positions.add(b);
        }
        double seam = seamSum / along;
        boolean ok = seam <= FACTOR * inside + SLACK;
        return new Seam(tile, axis, seam, inside, ok ? List.of() : List.copyOf(positions), ok);
    }

    private static char cell(Grid g, Axis axis, int across, int along) {
        return axis == Axis.X ? g.at(across, along) : g.at(along, across);
    }

    /** How different two neighbouring pixels look. */
    static double difference(Palette p, char c1, char c2) {
        if (c1 == c2) return 0;
        boolean e1 = c1 == Palette.TRANSPARENT, e2 = c2 == Palette.TRANSPARENT;
        if (e1 || e2) {
            Palette.Entry solid = p.get(e1 ? c2 : c1);
            return EMPTY_VS_SOLID * solid.alpha() / 255.0;
        }
        Palette.Entry a = p.get(c1), b = p.get(c2);
        return Colors.distance(a.rgb(), b.rgb()) + EMPTY_VS_SOLID * Math.abs(a.alpha() - b.alpha()) / 255.0;
    }

    /** "0-3, 7, 9-15". */
    static String ranges(List<Integer> sorted) {
        List<String> parts = new ArrayList<>();
        int i = 0;
        while (i < sorted.size()) {
            int j = i;
            while (j + 1 < sorted.size() && sorted.get(j + 1) == sorted.get(j) + 1) j++;
            parts.add(i == j ? String.valueOf(sorted.get(i)) : sorted.get(i) + "-" + sorted.get(j));
            i = j + 1;
        }
        return String.join(", ", parts);
    }
}
