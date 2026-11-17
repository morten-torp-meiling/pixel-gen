package pixelgen.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A parsed {@code .sprite} file.
 *
 * @param name     sprite name (from {@code sprite <name>}, or the file name)
 * @param width    frame width in pixels
 * @param height   frame height in pixels
 * @param palette  the palette every symbol comes from
 * @param style    the sprite's style ({@link Style#BUILT_IN} when neither the file nor the project names one)
 * @param anchorX  pivot point, for example the feet of a character; defaults to bottom centre
 * @param anchorY  pivot point y
 * @param parts    reusable pieces, in file order; each may have its own size
 * @param frames   finished full-size images (stamps, recolours, derivations and effects already
 *                 applied), in file order
 * @param anims    animations over the frames, in file order
 * @param variants recoloured copies of the whole sprite: variant name to symbol mapping
 * @param warnings non-fatal problems, such as a frame with no pixels
 */
public record Sprite(String name, int width, int height, Palette palette, Style style, int anchorX, int anchorY,
                     Map<String, Grid> parts, Map<String, Frame> frames, Map<String, Anim> anims,
                     Map<String, Map<Character, Character>> variants, List<String> warnings) {

    /** One finished image of the sprite. */
    public record Frame(String name, Grid grid) {}

    public Frame frame(String name) { return frames.get(name); }

    public Anim anim(String name) { return anims.get(name); }

    /** The variant as a sprite of its own: every part and frame recoloured, without further variants. */
    public Sprite variant(String variantName) {
        Map<Character, Character> map = variants.get(variantName);
        if (map == null) throw new IllegalArgumentException("no variant '" + variantName + "'");
        Map<String, Grid> newParts = new LinkedHashMap<>();
        parts.forEach((n, g) -> newParts.put(n, g.recolor(map)));
        Map<String, Frame> newFrames = new LinkedHashMap<>();
        frames.forEach((n, f) -> newFrames.put(n, new Frame(n, f.grid().recolor(map))));
        return new Sprite(variantName, width, height, palette, style, anchorX, anchorY, newParts, newFrames, anims,
                Map.of(), List.of());
    }

    /** This sprite followed by each of its variants. */
    public List<Sprite> withVariants() {
        List<Sprite> all = new ArrayList<>();
        all.add(this);
        for (String v : variants.keySet()) all.add(variant(v));
        return all;
    }
}
