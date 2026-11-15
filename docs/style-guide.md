# pixel-gen style guide

How the game's art should look, and the rules that get it there. It's written for whoever draws the sprites as text (mostly Claude), so every rule is something you can check in a grid. The file syntax is in [format.md](format.md).

**The look:** a cozy farming game seen from above at a slant (top-down 3/4 view), on a 16 px grid, with soft shading, clear outlines and a warm palette. The conventions (scale, perspective, light) are common to the genre; **every asset is original.** Don't copy, trace or closely imitate any existing game's sprites, characters, tiles or palette.

## 1. Scale and grid

One art pixel is one game pixel, always. Never scale a sprite up or down to fit; redraw it at the size it needs.

| Thing | Size (w × h) | Anchor | Notes |
|---|---|---|---|
| Ground tile | 16 × 16 | — | The unit of the world. |
| Character | 16 × 32 | `8 31` | Feet on row 30, row 31 empty for the shadow. |
| Small object, item, crop | 16 × 16 | bottom centre | A chest, a pot, a seedling. |
| Tall object, grown crop, bush | 16 × 32 | bottom centre | Base on the bottom rows. |
| Small tree | 32 × 48 | trunk base | Trunk 4–6 px wide. |
| Large tree, building | multiples of 16 | base centre | Footprint on whole tiles. |

- **Footprints sit on the grid.** An object's base (where it touches the ground) covers whole tiles; whatever rises above may overlap the tiles behind it.
- **Anchor = where it touches the ground.** Godot puts the anchor on the node's position, and with Y-sort on it draws by that position, so a character walks *behind* a tall object when its feet are above the object's base.
- Leave **1 px free around the art** in every frame: outlines and shadows are drawn there, and the tool warns when they're cut off.

## 2. Perspective: top-down 3/4

We look down at about 45°, so **both the top and the front of things show**.

- **Boxes, crates, chests:** the top surface takes about a third of the height, the front the rest. The art-test chest (16 × 16): lid top rows 4–6, front rows 7–14.
- **Upright things stay upright.** Walls, trunks, people and posts are drawn straight up, never leaning back.
- **Round things** (pots, barrels, stumps) show an ellipse on top, about half as tall as it is wide.
- **Ground is flat.** Tiles show no front faces; only things that stand up from the ground do.
- **Characters** face four ways: down (towards the viewer), up, right and left. Down shows the face; up shows the back of the head.

## 3. Light

**Light comes from the top left, everywhere,** for every sprite and every tile.

- **Top surfaces are the lightest, fronts the middle shades, undersides the darkest.**
- Within a surface, the **left edge is one shade lighter and the right edge one shade darker** than the middle.
- **A lit lip** (one row of the lightest shade) where a top surface meets a front reads as an edge catching the light.
- **A dark row directly under an overhang** (a lid, a roof, a hat brim) gives depth.
- **Cast shadows** fall straight down onto the ground under things. Characters and loose objects get the `shadow` effect (`shadow ~ 12 3` for a character); big fixed objects can have a shadow drawn into the ground pixels around their base.

Flipping a sprite mirrors its light: a frame made with `flipx` is lit from the top right. Small, symmetric things (most front views) don't show it; for side views, see section 9.

## 4. Palette and colour

Use **only `palettes/farm.pal`** unless a new palette is agreed on. Each ramp runs dark to light, and its shades already lean cool and purple in the shadows and warm in the lights, so shading with one ramp gives colour shifts for free.

- **Each material uses one ramp, 3–5 shades of it.** Wood: `W w V v`. Leaves: `F G g L l`. Skin: `J j S s`.
- **Shade by stepping along the ramp,** one step at a time. Skipping two steps reads as a hard edge, which is right for a crease or contact line and wrong for a curve.
- **Don't mix ramps for shading** (no brown shadows on green leaves). The one exception is the outline colours `K` and `k`.
- **Accents** (gold, red, violet) are for small things that should catch the eye: a lock, a flower, a blush. Keep them small.
- **Translucent colours are for effects only** (`~`, the shadow). Art pixels are opaque.

## 5. Outlines

- **`K` (near-black) is for the silhouette only:** the outer edge of a sprite against the world.
- **Lines inside a shape** (an arm against the torso, the line between lid and body, the folds of a shirt) use the soft outline `k` or the darkest shade of the material. Black lines inside a shape look heavy.
- **Selective outline:** on the lit top and left, the outline may take the darkest shade of the material it touches instead of `K`; `outline K selective` does this automatically. Use it on organic shapes (bushes, trees, crops) and soft materials. Keep the full `K` outline on characters, so they read against any ground.
- **Ground tiles have no outlines.** Neither do decals that lie flat on the ground (paths, puddles, flowers in the grass): their edge is a colour change.
- **Outlines are one pixel wide** and connect on the four sides (no diagonal-only joins, which look broken), as the `outline` effect draws them.

## 6. Clean pixels

These rules separate pixel art from a shrunken drawing. Check them in the review image's enlarged view.

**Smooth lines step evenly.** A slope is a run of equal steps (1:1, 2:1, 3:1) or a steadily changing series (3, 2, 1 for a curve). An uneven step is a "jaggy", a visible kink.

```
good, 2:1          bad, uneven
KK......           KK......
..KK....           ..K.....
....KK..           ...KKK..
......KK           ......KK
```

**No doubled pixels on lines.** Where a 1 px line steps, the pixels touch at corners, not sides; an extra pixel in the inner corner thickens the line.

```
good               bad, doubled
K.....             K.....
.K....             KK....
..K...             .KK...
```

**No pillow shading.** Shading that follows the outline all the way round (dark rim, light centre) makes everything look like a cushion. Shade from the light: light at the top left, dark at the bottom right.

```
good, lit top left      bad, pillow shaded
.KKKKKK.                .KKKKKK.
KvvVVVwK                KwwwwwwK
KvVVVwwK                KwVVVVwK
KVVVwwWK                KwVvvVwK
KVwwwWWK                KwVVVVwK
.KKKKKK.                .KKKKKK.
```

**No banding.** Two shades running side by side in parallel, following the same stair-steps, read as stripes rather than form. Vary the widths of the bands.

**Clusters, not specks.** Shades sit in clusters of 2 or more pixels. A single pixel of a different shade is noise, unless it's on purpose: an eye, a highlight glint, a speck of texture on a tile.

**No full-width dark rows.** A dark row across a whole object reads as a stripe. Wood grain, planks and stitching are short 2–3 px dashes, offset from row to row.

**No dithering** (checkerboard mixes of two shades) by default; at 16 px it looks noisy. Use more shades of the ramp instead.

## 7. Readability

- **Judge at 1× and 2×,** in the actual-size row of the review image, not only enlarged. Something that only reads at 8× doesn't read in the game.
- **The silhouette should say what it is** without any interior detail. Fill it with one colour in your head: is it still a chest, a person, a tree?
- **Contrast with the ground.** Most of the screen is green grass and brown soil; things standing on it need their outline, a lighter top, or a contrasting ramp to stand out.
- **Details sit on seams and edges:** a lock across the lid line, a buckle on a belt, a knot where a branch meets the trunk.
- **Volume comes from overlap:** a lid 1 px wider than the body, bands that wrap over the top, a brim that overhangs the face.

## 8. Characters

16 × 32, facing down, anchor `8 31`. The layout of `art-test/farmer.sprite`, moved up 1 px (that file predates the shadow rule, so its boots still fill row 31):

| Rows | What |
|---|---|
| 1–14 | Head and hair: a large head (almost half the height) keeps faces readable and cozy. |
| 14–15 | Chin and collar: a collar or neckline breaks the line between head and body. |
| 15–21 | Torso and arms. Arms are separated from the torso by `k`, not `K`. |
| 22 | Belt. |
| 23–30 | Legs and boots; the boots' outline ends on row 30. |
| 31 | Empty: the shadow (`shadow ~ 12 3`) shows here. |

**Faces at 16 px:**
- Eyes are 1 × 2 dark pixels, 4 px apart, centred.
- One blush pixel (`r`) under each eye; a 2 px mouth in the skin shadow colour.
- The face is at least 10 px wide; keep side hair short.
- A fringe is 2–3 clumps pointing down, with skin showing between them.

**Build characters from parts** (`head`, `torso`, `legs_*`), so every frame and direction reuses the same pieces and nothing drifts. **Clothes and hair colours are variants** (`variant farmer_blue recolor e>b E>B R>c`): draw one character, recolour the rest.

Front views are almost symmetric: draw one half carefully, mirror it, then fix the light (the right side one shade darker).

## 9. Animation

**Standard cycles**

| Animation | Frames | Speed | Notes |
|---|---|---|---|
| Walk | 4 | 8 fps, loop | Contact, passing, contact (other foot), passing: `walk_down fps 8 loop: down_1 down_0 down_2 down_0`. |
| Idle | 2–4 | 2–4 fps, loop | Breathing: the upper body (or head) 1 px down and back. Hold the rest pose longer: `idle fps 3 loop: rest*3 breathe`. |
| Tool swing | 3–5 | 10–12 fps, once | Wind-up, strike, follow-through; hold the strike: `swing fps 10 once: swing_0 swing_1*2 swing_2`. |
| Small loops (a flame, a sparkle) | 2–4 | 4–8 fps, loop | |

**Rules**

- **Move parts, not the whole frame.** Shifting the whole frame 1 px looks like the sprite jumps; move the parts that should move (upper body for breathing, legs for walking) and leave the rest where it is. Parts stamped at different heights per frame do this cleanly.
- **The anchor never moves.** Feet stay on row 30 in every frame, including jumps (draw the body higher, not the feet lower).
- **Keep the body stable in a walk.** Check with `pg review --onion`: in a good walk, the onion skin shows red only around the legs and arms. Red on the head or outline means the body jitters.
- **Every frame is a full pose.** Don't let a part vanish for a frame (a hand, an eye); it reads as a flicker.
- **Directions:** draw down, up and right; derive left with `frame left_0 from right_0 flipx`. The flip makes the left-facing frames lit from the top right. On a 16 px character that's usually acceptable. Where it shows (a strong highlight on one side), fix the left frames with `recolor`, or draw them.
- Watch it move: `pg gif` and open the GIF; judge the rhythm at 1×.

## 10. Tiles

- **16 × 16, no outlines, low contrast.** Ground is the background: texture with neighbouring shades of one ramp (`g` with specks of `G` and `L`), never anything that pops out.
- **Every base tile repeats seamlessly** both ways. `pg tilecheck` must pass. Texture may cross the edge, as long as it continues on the opposite side (a tuft split between the right and left columns).
- **Repetition shows.** A 4 × 4 field of one grass tile shows its pattern clearly. Draw 2–4 versions of each base tile (`tile grass_b from grass flipx`, plus hand-varied ones), and mix them on the map.
- **Avoid unique features in base tiles.** One big flower or rock in a grass tile is seen at every repeat; put it in a separate decoration tile or object instead.
- **Transitions** (grass meeting dirt) are their own tiles: an edge tile repeats only along its edge (`wrap x` for a top or bottom edge, `wrap y` for a side), and corners don't repeat (`wrap none`). The higher ground's edge gets a lit lip on the top and left, and a dark row where it drops on the bottom and right.
- **The same light everywhere:** tiles follow section 3 like everything else.

## 11. Self-review checklist

Go through it for every sprite before calling it done:

1. `pg validate` passes, with no warnings left unexplained.
2. `pg review`: at 1× and 2× the silhouette reads, and the thing stands out from grass and soil.
3. Light from the top left: tops lightest, left edges lighter, right edges and undersides darker, cast shadow below.
4. `K` only on the silhouette; inside lines use `k` or the material's darkest shade.
5. No jaggies, doubled pixels, pillow shading, banding, stray specks or full-width dark rows.
6. Only palette colours; one ramp per material; accents kept small.
7. The anchor is at the base, with 1 px free around the art.
8. Animations: `pg review --onion` shows only the moving parts in red; the GIF's rhythm looks right at 1×.
9. Tiles: `pg tilecheck` passes, and the repeat looks even, without a visible pattern.
