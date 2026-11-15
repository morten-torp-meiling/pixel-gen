package pixelgen.parse;

import pixelgen.model.Colors;
import pixelgen.model.Grid;
import pixelgen.model.Palette;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts an image into a grid of palette symbols, so existing art can be edited as text.
 * <ul>
 *   <li>If the palette has no translucent colours, pixels with alpha below 128 become transparent
 *       ({@code '.'}) and the rest count as opaque. If it has some, opacity is matched too:
 *       a 33% black pixel becomes the palette's 33% shadow colour, and a nearly invisible one {@code '.'}.</li>
 *   <li>Each colour maps to the perceptually nearest palette colour (OKLab distance).</li>
 *   <li>An enlarged image (every pixel a solid N×N block) is shrunk back to 1:1 first.</li>
 * </ul>
 */
public final class PngImporter {

    /** Matches farther apart than this (OKLab distance) are flagged as poor. */
    public static final double POOR_MATCH = 0.08;

    private PngImporter() {}

    /** A source colour that had no exact palette match; symbol {@code .} means it became "no pixel". */
    public record Mismatch(int rgb, int alpha, int pixels, char symbol, int paletteRgb, int paletteAlpha,
                           double distance) {
        public boolean poor() { return distance > POOR_MATCH; }
    }

    /**
     * The imported grid plus what was approximated.
     *
     * @param scale            the block size the image was shrunk by (1 = not enlarged)
     * @param exactColours     distinct opaque colours that matched the palette exactly
     * @param mismatches       colours that were replaced, most used first
     * @param semiTransparent  pixels with partial alpha that were treated as opaque
     */
    public record Result(Grid grid, int scale, int exactColours, List<Mismatch> mismatches, int semiTransparent) {}

    /** Imports with automatic scale detection. */
    public static Result importImage(BufferedImage img, Palette palette) {
        return importImage(img, palette, detectScale(img));
    }

    public static Result importImage(BufferedImage img, Palette palette, int scale) {
        if (scale < 1 || img.getWidth() % scale != 0 || img.getHeight() % scale != 0) {
            throw new IllegalArgumentException("scale " + scale + " does not divide the image size "
                    + img.getWidth() + "x" + img.getHeight());
        }
        boolean alphaAware = palette.hasTranslucent();
        int w = img.getWidth() / scale, h = img.getHeight() / scale;
        Map<Integer, Integer> counts = new LinkedHashMap<>();  // colour key (argb) -> pixel count
        int semi = 0;
        int[][] keys = new int[h][w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                // Sample the block's top-left pixel; detectScale guarantees uniform blocks.
                int p = img.getRGB(x * scale, y * scale);
                int alpha = p >>> 24;
                int key;
                if (alphaAware) {
                    key = alpha == 0 ? 0 : p;  // partial alpha is matched against translucent colours
                } else if (alpha < 128) {
                    key = 0;
                } else {
                    if (alpha < 255) semi++;
                    key = 0xFF000000 | p;
                }
                keys[y][x] = key;
                if (key != 0) counts.merge(key, 1, Integer::sum);
            }
        }

        Map<Integer, Character> mapping = new LinkedHashMap<>();
        List<Mismatch> mismatches = new ArrayList<>();
        int exact = 0;
        for (Map.Entry<Integer, Integer> c : counts.entrySet()) {
            int argb = c.getKey();
            Palette.Entry best = alphaAware ? nearestWithAlpha(argb, palette) : nearest(argb & 0xFFFFFF, palette);
            mapping.put(argb, best == null ? Palette.TRANSPARENT : best.symbol());
            if (best != null && best.argb() == argb) {
                exact++;
            } else if (best == null) {
                mismatches.add(new Mismatch(argb & 0xFFFFFF, argb >>> 24, c.getValue(), Palette.TRANSPARENT, 0, 0,
                        cost(argb, 0, 0)));
            } else {
                mismatches.add(new Mismatch(argb & 0xFFFFFF, argb >>> 24, c.getValue(), best.symbol(), best.rgb(),
                        best.alpha(), alphaAware ? cost(argb, best.rgb(), best.alpha())
                        : Colors.distance(argb & 0xFFFFFF, best.rgb())));
            }
        }
        mismatches.sort(Comparator.comparingInt(Mismatch::pixels).reversed());

        List<String> rows = new ArrayList<>(h);
        for (int y = 0; y < h; y++) {
            StringBuilder row = new StringBuilder(w);
            for (int x = 0; x < w; x++) {
                int key = keys[y][x];
                row.append(key == 0 ? Palette.TRANSPARENT : mapping.get(key));
            }
            rows.add(row.toString());
        }
        return new Result(new Grid(rows), scale, exact, mismatches, semi);
    }

    /** The palette colour perceptually closest to {@code rgb}; an exact match always wins. */
    public static Palette.Entry nearest(int rgb, Palette palette) {
        Palette.Entry best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Palette.Entry e : palette.entries()) {
            if (e.translucent()) continue;
            if (e.rgb() == rgb) return e;
            double d = Colors.distance(rgb, e.rgb());
            if (d < bestDistance) {
                bestDistance = d;
                best = e;
            }
        }
        return best;
    }

    /**
     * The palette colour closest to {@code argb} in colour and opacity, or null when "no pixel"
     * is closer (a faint pixel). An exact match always wins.
     */
    public static Palette.Entry nearestWithAlpha(int argb, Palette palette) {
        Palette.Entry best = null;
        double bestCost = cost(argb, 0, 0);  // the cost of '.'
        for (Palette.Entry e : palette.entries()) {
            if (e.argb() == argb) return e;
            double d = cost(argb, e.rgb(), e.alpha());
            if (d < bestCost) {
                bestCost = d;
                best = e;
            }
        }
        return best;
    }

    /** Colour distance plus opacity difference (0-1); opacity alone counts against "no pixel" (alpha 0). */
    private static double cost(int argb, int rgb, int alpha) {
        double alphaDiff = Math.abs((argb >>> 24) - alpha) / 255.0;
        return alpha == 0 ? alphaDiff : Colors.distance(argb & 0xFFFFFF, rgb) + alphaDiff;
    }

    /**
     * The largest block size N such that the image consists of solid N×N blocks; 1 if none.
     * Fully transparent pixels count as one value whatever their RGB, so enlarged transparency is recognised.
     */
    public static int detectScale(BufferedImage img) {
        int g = gcd(img.getWidth(), img.getHeight());
        for (int s = g; s > 1; s--) {
            if (g % s == 0 && uniformBlocks(img, s)) return s;
        }
        return 1;
    }

    private static boolean uniformBlocks(BufferedImage img, int s) {
        for (int by = 0; by < img.getHeight(); by += s) {
            for (int bx = 0; bx < img.getWidth(); bx += s) {
                int first = normalise(img.getRGB(bx, by));
                for (int y = by; y < by + s; y++) {
                    for (int x = bx; x < bx + s; x++) {
                        if (normalise(img.getRGB(x, y)) != first) return false;
                    }
                }
            }
        }
        return true;
    }

    private static int normalise(int argb) { return (argb >>> 24) == 0 ? 0 : argb; }

    private static int gcd(int a, int b) { return b == 0 ? a : gcd(b, a % b); }

    /** The {@code .sprite} text for a single imported frame. */
    public static String toSpriteText(String spriteName, String paletteName, String frameName, Grid grid,
                                      String sourceNote) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Imported from ").append(sourceNote).append('\n');
        sb.append("sprite ").append(spriteName).append('\n');
        sb.append("size ").append(grid.width()).append(' ').append(grid.height()).append('\n');
        sb.append("palette ").append(paletteName).append("\n\n");
        sb.append("frame ").append(frameName).append('\n');
        for (String row : grid.rows()) sb.append("  ").append(row).append('\n');
        sb.append("end\n");
        return sb.toString();
    }
}
