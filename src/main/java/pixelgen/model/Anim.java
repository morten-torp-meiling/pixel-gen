package pixelgen.model;

import java.util.List;

/**
 * A named animation: frames of a sprite played in order at a fixed rate. Matches Godot's
 * SpriteFrames, where each frame has a relative duration (1 = one tick at {@code fps}).
 *
 * @param name   animation name, such as {@code walk_down}
 * @param fps    ticks per second
 * @param loop   true to repeat, false to stop on the last frame
 * @param steps  the frames in play order; a frame may appear more than once
 */
public record Anim(String name, double fps, boolean loop, List<Step> steps) {

    /** One frame of the animation, shown for {@code duration} ticks. */
    public record Step(String frame, double duration) {}

    public Anim {
        steps = List.copyOf(steps);
    }

    /** How long one pass takes, in seconds. */
    public double seconds() {
        return steps.stream().mapToDouble(Step::duration).sum() / fps;
    }
}
