---
name: pixel-animate
description: Animate a pixel-art sprite for the Godot game (character walk, idle, tool swing or other cycle, in up to four directions, or a looping effect like a flame) as frames and anims in a .sprite file, check it with onion-skin strips and GIFs, and export it. Use whenever the user asks in this project to animate something, add a walk, idle or swing, add a direction, or fix how an animation moves. For still sprites use pixel-sprite; for ground tiles use pixel-tileset.
---

# Animating a sprite

An animation is a list of frames played at a rate (`anim walk_down fps 8 loop: down_1 down_0 down_2 down_0`). Good animation here is mostly **structure**: a character built from parts, so each frame moves only what should move, and nothing else drifts.

Read before animating (once per session):
- `docs/style-guide.md`: sections 8 (characters) and 9 (animation), plus 3 for the light.
- `docs/format.md`: parts, `stamp`, derived frames and `anim`.
- The `pixel-sprite` skill for the tool setup and drawing workflow. Run `./gradlew installDist -q` first; `pg` below means `build/install/pixel-gen/bin/pixel-gen`.

## 1. Plan the set

- **Which animations and directions.** A character usually needs `idle_<dir>` and `walk_<dir>` for `down`, `up`, `right` and `left`, then actions (`swing_<dir>`, `water_<dir>`). Name animations `<action>_<direction>` and frames `<direction>_<n>`, or `<action>_<direction>_<n>` for actions: game code plays them by name.
- **Frame counts and speeds** from the table in style-guide section 9: walk 4 frames at 8 fps, idle 2–4 at 2–4 fps, swing 3–5 at 10–12 fps `once`.
- **Draw one direction completely first** (usually `down`), check it, and only then do the others. Mistakes found in the first direction are cheap; found after four, they cost four times.

## 2. Split the sprite into parts

Decide what moves in each animation, and make each moving piece a part. For a 16×32 character:

| Part | Rows (16 × 32) | Moves in |
|---|---|---|
| `top` (head, torso, arms) | 0–24 | bob in walk, breathing in idle |
| `legs_stand`, `legs_l`, `legs_r` | 25–31 | walk (one per leg pose) |
| `arm_*` (only if separate) | as needed | swings, carrying, arm swing in walk |

- A part holds a **pose**, not a frame: `legs_l` (left leg forward) is used by every frame with that leg pose.
- **Stamp order is depth order:** later stamps go on top. Stamp what's behind first (back arm, then body, then front arm).
- **Where a moving part overlaps a fixed one, stamp the fixed one last.** When `top` bobs down 1 px, its bottom row lands on the legs' top row; stamping the legs after the body keeps the hips the same in every frame, and the body appears to settle into them.
- Keep each part's **outline inside the part**, so stamping it anywhere gives a closed shape. Where parts meet (waist, shoulder), let their outlines overlap on the same row.
- A part grid can be the frame width with `.` rows above, so stamping at `0 <y>` keeps the columns fixed and only `y` changes per frame.

## 3. Build the frames

Each frame is a list of stamps; the frame is the pose.

```
frame down_1          # contact: left foot forward, body down 1 px
  stamp top 0 1
  stamp legs_l 0 25
end
frame down_0          # passing: legs together, body up
  stamp top 0 0
  stamp legs_stand 0 25
end
```

Rules from the style guide that matter most here:
- **Move parts, not the whole frame.** Body bob = the `top` part 1 px lower on contact frames, while the legs stay put. `frame x from y shift 0 1` moves the feet too, which looks like the whole sprite jumps.
- **The anchor never moves:** feet end on row 30 in every frame, row 31 stays empty for the shadow. Put `shadow ~ 12 3` at the sprite level, so every frame gets the same shadow.
- **Every frame is a full pose.** No part disappears for a frame.

### Standard cycles

- **Walk (down/up), 4 frames:** contact (one foot forward, body down 1 px), passing (feet together, body up), contact with the other foot, passing again: `walk_down fps 8 loop: down_1 down_0 down_2 down_0`. The up direction works the same, seen from behind.
- **Walk (right), 4 frames:** contact (legs apart, body down), passing (legs together, body up), contact (other leg in front), passing. In a side view the front leg and arm swing opposite each other.
- **Idle, 2–4 frames:** the rest pose held long, then a breath (`top` 1 px lower): `idle_down fps 3 loop: down_0*3 breathe_down`. No leg movement.
- **Tool swing, 3–5 frames, `once`:** wind-up, strike (held: `swing_down_1*2`), follow-through. The strike is the frame the game acts on; tell the user which one it is.
- **Looping effects (flame, sparkle):** 2–4 frames at 4–8 fps; keep the base still and move only the tips.

### Left from right

`frame left_0 from right_0 flipx` makes the left frames free, and `anim walk_left fps 8 loop: left_1 left_0 left_2 left_0` uses them. The flip mirrors the light: left-facing frames are lit from the top right. On a 16 px character that's usually fine. If it shows (a strong highlight on one side), add `recolor` to the derived frames or draw them. Tell the user it's flipped.

## 4. Check the motion

After each change, run all three and **open the images with the Read tool**:

```bash
pg validate <file>
pg review <file> --strip     # every animation as a row of frames, in play order
pg review <file> --onion     # the same, with the previous frame's pixels in red where this one has none
```

- **The strip** shows the cycle at a glance: are the contact frames mirror images, does the passing frame sit between them?
- **The onion skin** shows what moved. In a good walk, red appears only at the legs and feet, plus the 1 px bob line at the top of the head and shoulders. Red anywhere else means a part drifts or jitters: compare the frames' stamps.
- **Counting:** use the coordinates on the image edges. "The hair moves 1 px left in down_2" is a finding; "it looks off" isn't.

Then make the GIF for the user (you can't watch it play yourself):

```bash
pg gif <file> --anim walk_down --scale 4
```

## 5. Show and export

Send the user the strip, the onion image and the GIF(s), with the frame counts, speeds and anything flipped or uncertain. Their eye on the GIF is the real test of the rhythm; ask before polishing further.

Then `pg export` (if `pixelgen.conf` exists). In Godot the animations keep their names, speeds, looping and frame durations; the generated scene autoplays the first animation in the file, so put the main one (`idle_down`) first. Never commit `pixelgen.conf` or anything from the user's Godot project.
