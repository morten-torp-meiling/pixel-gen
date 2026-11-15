package pixelgen.godot;

import pixelgen.model.Anim;
import pixelgen.model.Sprite;
import pixelgen.render.SheetPacker;

import java.awt.Rectangle;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes a Godot 4 {@code SpriteFrames} resource ({@code .tres}, text format 3) for a packed sheet.
 * <ul>
 *   <li>The sheet PNG is the one external resource, referenced by its {@code res://} path.</li>
 *   <li>Each frame becomes one {@code AtlasTexture} with its region on the sheet, shared by every
 *       animation that shows it. {@code filter_clip} stops a frame picking up its neighbours' pixels.</li>
 *   <li>Each {@code anim} becomes an animation with the same name, speed (fps), looping and
 *       per-frame durations; Godot's durations are relative ticks, exactly like ours.</li>
 *   <li>Frames no animation uses become one-frame animations named after the frame, so still
 *       sprites (a chest that is closed or open) can be switched with {@code play("open")}.</li>
 * </ul>
 * The output is deterministic (no random ids or uids), so it can be compared and diffed. Godot
 * adds a uid of its own the first time the editor saves the file.
 */
public final class SpriteFramesWriter {

    /** fps for the one-frame animations of still frames; it doesn't matter, as nothing moves. */
    static final double STILL_FPS = 5;

    private SpriteFramesWriter() {}

    /**
     * What was written: the text, the animation names in order, and still frames left out because
     * an animation already has their name.
     */
    public record Result(String text, List<String> animations, List<String> skipped) {}

    /**
     * @param sprite      the sprite (or a variant: the layout is the same)
     * @param layout      where each frame sits on the sheet
     * @param texturePath the sheet's path inside the Godot project, such as {@code res://sprites/farmer.png}
     */
    public static Result write(Sprite sprite, SheetPacker.Layout layout, String texturePath) {
        List<Anim> anims = new ArrayList<>(sprite.anims().values());
        List<String> skipped = new ArrayList<>();
        for (SheetPacker.Row row : layout.rows()) {
            if (row.anim() != null) continue;
            for (String frame : row.frames()) {
                if (sprite.anims().containsKey(frame)) skipped.add(frame);
                else anims.add(new Anim(frame, STILL_FPS, false, List.of(new Anim.Step(frame, 1))));
            }
        }

        // Only frames some animation shows need a texture.
        Map<String, Rectangle> used = new LinkedHashMap<>();
        layout.frames().forEach((name, r) -> {
            if (anims.stream().anyMatch(a -> a.steps().stream().anyMatch(s -> s.frame().equals(name)))) {
                used.put(name, r);
            }
        });

        StringBuilder t = new StringBuilder();
        // load_steps counts every resource in the file plus the main one; newer Godot ignores it.
        t.append("[gd_resource type=\"SpriteFrames\" load_steps=").append(used.size() + 2).append(" format=3]\n\n");
        t.append("[ext_resource type=\"Texture2D\" path=").append(quote(texturePath)).append(" id=\"1_sheet\"]\n\n");
        used.forEach((name, r) -> {
            t.append("[sub_resource type=\"AtlasTexture\" id=").append(quote(textureId(name))).append("]\n");
            t.append("atlas = ExtResource(\"1_sheet\")\n");
            t.append("region = Rect2(").append(r.x).append(", ").append(r.y).append(", ").append(r.width)
                    .append(", ").append(r.height).append(")\n");
            t.append("filter_clip = true\n\n");
        });
        t.append("[resource]\n");
        t.append("animations = [");
        for (int i = 0; i < anims.size(); i++) {
            Anim a = anims.get(i);
            t.append(i == 0 ? "{\n" : ", {\n");
            t.append("\"frames\": [");
            for (int j = 0; j < a.steps().size(); j++) {
                Anim.Step s = a.steps().get(j);
                t.append(j == 0 ? "{\n" : ", {\n");
                t.append("\"duration\": ").append(number(s.duration())).append(",\n");
                t.append("\"texture\": SubResource(").append(quote(textureId(s.frame()))).append(")\n");
                t.append("}");
            }
            t.append("],\n");
            t.append("\"loop\": ").append(a.loop()).append(",\n");
            t.append("\"name\": &").append(quote(a.name())).append(",\n");
            t.append("\"speed\": ").append(number(a.fps())).append("\n");
            t.append("}");
        }
        t.append("]\n");
        return new Result(t.toString(), anims.stream().map(Anim::name).toList(), List.copyOf(skipped));
    }

    private static String textureId(String frame) { return "AtlasTexture_" + frame; }

    /** Godot's float style: always a decimal point, no exponent ({@code 8.0}, {@code 0.5}). */
    static String number(double v) {
        String s = BigDecimal.valueOf(v).stripTrailingZeros().toPlainString();
        return s.contains(".") ? s : s + ".0";
    }

    static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
