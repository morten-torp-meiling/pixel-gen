package pixelgen.render;

import pixelgen.model.Grid;
import pixelgen.model.Palette;

import java.awt.image.BufferedImage;

/**
 * Turns grids into images. Each grid cell becomes one pixel: fully opaque in its palette colour,
 * or fully transparent for {@code '.'}. Scaling only ever repeats pixels (nearest neighbour), so
 * pixel art stays crisp.
 */
public final class Renderer {

    /** Largest scale factor accepted, and the largest image side produced. */
    public static final int MAX_SCALE = 64;
    public static final int MAX_SIDE = 8192;

    private Renderer() {}

    /** Renders a grid at 1:1. Throws IllegalArgumentException for a symbol the palette lacks. */
    public static BufferedImage render(Grid grid, Palette palette) {
        BufferedImage img = new BufferedImage(grid.width(), grid.height(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < grid.height(); y++) {
            for (int x = 0; x < grid.width(); x++) {
                char c = grid.at(x, y);
                if (c == Palette.TRANSPARENT) continue;  // new images start fully transparent
                Palette.Entry e = palette.get(c);
                if (e == null) {
                    throw new IllegalArgumentException("'" + c + "' at (" + x + "," + y + ") is not in palette '"
                            + palette.name() + "'");
                }
                img.setRGB(x, y, 0xFF000000 | e.rgb());
            }
        }
        return img;
    }

    /** Renders a grid and scales it up by a whole number. */
    public static BufferedImage render(Grid grid, Palette palette, int scale) {
        return scale(render(grid, palette), scale);
    }

    /** Nearest-neighbour upscale: every pixel becomes a {@code scale × scale} block. */
    public static BufferedImage scale(BufferedImage src, int scale) {
        if (scale < 1 || scale > MAX_SCALE) {
            throw new IllegalArgumentException("scale must be between 1 and " + MAX_SCALE + ", not " + scale);
        }
        if (scale == 1) return src;
        int w = src.getWidth() * scale, h = src.getHeight() * scale;
        if (w > MAX_SIDE || h > MAX_SIDE) {
            throw new IllegalArgumentException("scaled image would be " + w + "x" + h + "; the limit is "
                    + MAX_SIDE + " per side");
        }
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        int[] row = new int[w];
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                int argb = src.getRGB(x, y);
                for (int i = 0; i < scale; i++) row[x * scale + i] = argb;
            }
            for (int i = 0; i < scale; i++) out.setRGB(0, y * scale + i, w, 1, row, 0, w);
        }
        return out;
    }
}
