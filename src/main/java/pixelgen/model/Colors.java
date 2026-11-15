package pixelgen.model;

/**
 * Colour helpers. Colours are packed 0xRRGGBB ints (no alpha) unless stated otherwise.
 * Perceptual comparisons use OKLab, where L is lightness from 0 (black) to 1 (white).
 */
public final class Colors {

    private Colors() {}

    public static int red(int rgb) { return (rgb >> 16) & 0xFF; }
    public static int green(int rgb) { return (rgb >> 8) & 0xFF; }
    public static int blue(int rgb) { return rgb & 0xFF; }

    public static String toHex(int rgb) {
        return String.format("#%06x", rgb & 0xFFFFFF);
    }

    /** Parses {@code #rrggbb} or {@code rrggbb}. Throws IllegalArgumentException otherwise. */
    public static int parseHex(String text) {
        String s = text.startsWith("#") ? text.substring(1) : text;
        if (s.length() != 6 || !s.chars().allMatch(c -> Character.digit(c, 16) >= 0)) {
            throw new IllegalArgumentException("'" + text + "' is not a colour in the form #rrggbb");
        }
        return Integer.parseInt(s, 16);
    }

    /** {@code #rrggbb}, or {@code #rrggbbaa} when {@code alpha} is below 255. */
    public static String toHex(int rgb, int alpha) {
        return alpha >= 255 ? toHex(rgb) : toHex(rgb) + String.format("%02x", alpha & 0xFF);
    }

    /**
     * Parses {@code #rrggbb} (opaque) or {@code #rrggbbaa} into a packed 0xAARRGGBB int.
     * Throws IllegalArgumentException otherwise.
     */
    public static int parseHexWithAlpha(String text) {
        String s = text.startsWith("#") ? text.substring(1) : text;
        if ((s.length() != 6 && s.length() != 8) || !s.chars().allMatch(c -> Character.digit(c, 16) >= 0)) {
            throw new IllegalArgumentException("'" + text + "' is not a colour in the form #rrggbb or #rrggbbaa");
        }
        int rgb = Integer.parseInt(s.substring(0, 6), 16);
        int alpha = s.length() == 8 ? Integer.parseInt(s.substring(6), 16) : 0xFF;
        return (alpha << 24) | rgb;
    }

    /** OKLab coordinates {L, a, b} of an sRGB colour. */
    public static double[] oklab(int rgb) {
        double r = linear(red(rgb)), g = linear(green(rgb)), b = linear(blue(rgb));
        double l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b);
        double m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b);
        double s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);
        return new double[] {
                0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
                1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
                0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s,
        };
    }

    /** Perceived lightness, OKLab L, from 0 to 1. */
    public static double lightness(int rgb) {
        return oklab(rgb)[0];
    }

    /** Perceptual distance between two colours (Euclidean distance in OKLab). */
    public static double distance(int rgb1, int rgb2) {
        double[] p = oklab(rgb1), q = oklab(rgb2);
        double dl = p[0] - q[0], da = p[1] - q[1], db = p[2] - q[2];
        return Math.sqrt(dl * dl + da * da + db * db);
    }

    private static double linear(int channel) {
        double c = channel / 255.0;
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }
}
