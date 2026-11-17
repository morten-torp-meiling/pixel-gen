package pixelgen.render;

import pixelgen.model.Anim;
import pixelgen.model.Grid;
import pixelgen.model.Sprite;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static pixelgen.render.ReviewSheet.BACKGROUND;
import static pixelgen.render.ReviewSheet.GAP;
import static pixelgen.render.ReviewSheet.LABEL_H;
import static pixelgen.render.ReviewSheet.MAX_WIDTH;
import static pixelgen.render.ReviewSheet.PAD;
import static pixelgen.render.ReviewSheet.TEXT;
import static pixelgen.render.ReviewSheet.TEXT_DIM;
import static pixelgen.render.ReviewSheet.TITLE_H;

/**
 * Every animation laid out as a strip, one cell per step in play order, so a whole cycle can be
 * checked in one image. With onion skin, each cell also shows the previous step's solid pixels in
 * red where the current frame has none: parts that jump, or jitter between frames, stand out.
 */
public final class AnimSheet {

    public static final int ROW_LABEL_H = 22;

    private AnimSheet() {}

    /** The sheet plus each animation's cell areas (the enlarged grids), in step order. */
    public record Result(BufferedImage image, int scale, Map<String, List<Rectangle>> cells) {}

    public static Result render(Sprite sprite, int scale, boolean onion) {
        if (sprite.anims().isEmpty()) throw new IllegalArgumentException("sprite '" + sprite.name() + "' has no anims");
        int cellW = sprite.width() * scale, cellH = LABEL_H + sprite.height() * scale;

        // ---- layout: one block per anim, its cells wrapping at MAX_WIDTH ----
        Map<String, List<Rectangle>> cells = new LinkedHashMap<>();
        Map<String, Integer> labelY = new LinkedHashMap<>();
        int y = PAD + TITLE_H, width = 560;
        for (Anim a : sprite.anims().values()) {
            labelY.put(a.name(), y);
            y += ROW_LABEL_H;
            int x = PAD;
            List<Rectangle> rects = new ArrayList<>();
            for (int i = 0; i < a.steps().size(); i++) {
                if (x > PAD && x + cellW + PAD > MAX_WIDTH) {
                    x = PAD;
                    y += cellH + GAP;
                }
                rects.add(new Rectangle(x, y + LABEL_H, cellW, sprite.height() * scale));
                width = Math.max(width, x + cellW + PAD);
                x += cellW + GAP;
            }
            cells.put(a.name(), rects);
            y += cellH + GAP;
        }
        int height = y - GAP + PAD;

        BufferedImage img = ReviewSheet.newImage(width, height);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(BACKGROUND);
            g.fillRect(0, 0, width, height);
            g.setColor(TEXT);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
            g.drawString(sprite.name() + "  " + (onion ? "onion skin (previous frame in red)" : "animation strips")
                    + "  scale " + scale + "x", PAD, PAD + 16);

            for (Anim a : sprite.anims().values()) {
                g.setColor(TEXT);
                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
                g.drawString(String.format(Locale.ROOT, "%s   %s fps, %s, %.2f s", a.name(), Anim.number(a.fps()),
                        a.loop() ? "loop" : "once", a.seconds()), PAD, labelY.get(a.name()) + 15);
                List<Rectangle> rects = cells.get(a.name());
                for (int i = 0; i < a.steps().size(); i++) {
                    Anim.Step step = a.steps().get(i);
                    Rectangle r = rects.get(i);
                    g.setColor(TEXT_DIM);
                    g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
                    g.drawString((i + 1) + ". " + step.frame()
                            + (step.duration() == 1 ? "" : "  x" + Anim.number(step.duration())), r.x, r.y - 5);
                    Grid grid = sprite.frame(step.frame()).grid();
                    ReviewSheet.drawPixels(g, grid, sprite.palette(), r.x, r.y, scale,
                            onion ? previous(sprite, a, i) : null);
                    ReviewSheet.drawAnchor(g, sprite, r.x, r.y, scale);
                    g.setColor(TEXT_DIM);
                    g.drawRect(r.x - 1, r.y - 1, r.width + 1, r.height + 1);
                }
            }
        } finally {
            g.dispose();
        }
        return new Result(img, scale, cells);
    }

    /** The grid shown just before step i: the step before it, or for i = 0 the last step if looping. */
    static Grid previous(Sprite sprite, Anim a, int i) {
        if (i > 0) return sprite.frame(a.steps().get(i - 1).frame()).grid();
        if (a.loop() && a.steps().size() > 1) return sprite.frame(a.steps().getLast().frame()).grid();
        return null;
    }
}
