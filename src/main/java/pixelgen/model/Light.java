package pixelgen.model;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Where the light comes from, one of the four diagonals. A style sets it; selective outlines and
 * terrain shading follow it. {@code lx} and {@code ly} point towards the light: -1 is left or up,
 * +1 is right or down.
 */
public enum Light {
    TOP_LEFT("top-left", -1, -1),
    TOP_RIGHT("top-right", 1, -1),
    BOTTOM_LEFT("bottom-left", -1, 1),
    BOTTOM_RIGHT("bottom-right", 1, 1);

    private final String word;
    private final int lx, ly;

    Light(String word, int lx, int ly) {
        this.word = word;
        this.lx = lx;
        this.ly = ly;
    }

    /** As written in files: {@code top-left}. */
    public String word() { return word; }

    /** -1 when the light comes from the left, +1 from the right. */
    public int lx() { return lx; }

    /** -1 when the light comes from the top, +1 from the bottom. */
    public int ly() { return ly; }

    /** The light for a word such as {@code top-right}, or null. */
    public static Light parse(String word) {
        for (Light l : values()) if (l.word.equals(word)) return l;
        return null;
    }

    /** "top-left, top-right, bottom-left or bottom-right", for messages. */
    public static String choices() {
        String all = Arrays.stream(values()).map(Light::word).collect(Collectors.joining(", "));
        int last = all.lastIndexOf(", ");
        return all.substring(0, last) + " or " + all.substring(last + 2);
    }
}
