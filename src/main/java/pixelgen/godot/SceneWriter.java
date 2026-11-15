package pixelgen.godot;

import pixelgen.model.Sprite;

import static pixelgen.godot.SpriteFramesWriter.quote;

/**
 * Writes a ready-made Godot 4 scene ({@code .tscn}) for a sprite: one AnimatedSprite2D using the
 * sprite's SpriteFrames, set up the way pixel art needs it.
 * <ul>
 *   <li>texture filter Nearest, so pixels stay sharp;</li>
 *   <li>centered off and offset minus the anchor, so the anchor (a character's feet) sits on the
 *       node's position;</li>
 *   <li>the first animation selected and autoplaying.</li>
 * </ul>
 * The export owns this file and rewrites it; put the scene inside your own scenes rather than
 * editing it.
 */
public final class SceneWriter {

    /** Godot's CanvasItem.TEXTURE_FILTER_NEAREST. */
    static final int TEXTURE_FILTER_NEAREST = 1;

    private SceneWriter() {}

    /**
     * @param sprite     the sprite (or variant), for its name and anchor
     * @param firstAnim  the animation to select and autoplay, or null for none
     * @param framesPath the SpriteFrames {@code .tres}, such as {@code res://art/farmer.tres}
     */
    public static String write(Sprite sprite, String firstAnim, String framesPath) {
        StringBuilder t = new StringBuilder();
        t.append("[gd_scene load_steps=2 format=3]\n\n");
        t.append("[ext_resource type=\"SpriteFrames\" path=").append(quote(framesPath)).append(" id=\"1_frames\"]\n\n");
        t.append("[node name=").append(quote(sprite.name())).append(" type=\"AnimatedSprite2D\"]\n");
        t.append("texture_filter = ").append(TEXTURE_FILTER_NEAREST).append('\n');
        t.append("sprite_frames = ExtResource(\"1_frames\")\n");
        if (firstAnim != null) {
            t.append("animation = &").append(quote(firstAnim)).append('\n');
            t.append("autoplay = ").append(quote(firstAnim)).append('\n');
        }
        t.append("centered = false\n");
        t.append("offset = Vector2(").append(-sprite.anchorX()).append(", ").append(-sprite.anchorY()).append(")\n");
        return t.toString();
    }
}
