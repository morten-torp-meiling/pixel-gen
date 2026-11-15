package pixelgen.model;

import java.util.List;
import java.util.Map;

/**
 * A parsed {@code .sprite} file.
 *
 * @param name     sprite name (from {@code sprite <name>}, or the file name)
 * @param width    frame width in pixels
 * @param height   frame height in pixels
 * @param palette  the palette every symbol comes from
 * @param anchorX  pivot point, for example the feet of a character; defaults to bottom centre
 * @param anchorY  pivot point y
 * @param parts    reusable pieces, in file order; each may have its own size
 * @param frames   full-size images, in file order
 * @param warnings non-fatal problems, such as a frame with no pixels
 */
public record Sprite(String name, int width, int height, Palette palette, int anchorX, int anchorY,
                     Map<String, Grid> parts, Map<String, Frame> frames, List<String> warnings) {

    /** One image of the sprite. Later steps add frames built from parts and other frames. */
    public record Frame(String name, Grid grid) {}

    public Frame frame(String name) { return frames.get(name); }
}
