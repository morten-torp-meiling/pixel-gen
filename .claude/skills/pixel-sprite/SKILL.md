---
name: pixel-sprite
description: Draw a still pixel-art sprite for the Godot game (an object, item, crop, prop, piece of furniture, building part, or one character pose) as a .sprite text file, review it against the style guide, and export it. Use whenever the user asks to draw, make, design, redraw or fix a sprite, object or item in this project, such as "a wooden chest" or "a watering can". For animations use pixel-animate; for ground tiles use pixel-tileset.
---

# Drawing a still sprite

You draw the sprite as text: a grid of palette symbols in a `.sprite` file. The pixel-gen tool checks it, renders images you look at, and exports it to Godot. The art is only as good as your review rounds, so **always look at the rendered image** before showing anything.

Read before drawing (once per session):
- `docs/style-guide.md`: sections 1–7 and the checklist in 11. These are the rules you're judged by.
- `docs/format.md`: the sprite format, when unsure about syntax.
- `palettes/farm.pal`: the symbols and their ramps.

## The tool

```bash
./gradlew installDist -q          # first, every session: makes sure the launcher is current
build/install/pixel-gen/bin/pixel-gen <command>   # from Git Bash; written "pg" below
```

In PowerShell the launcher is `build\install\pixel-gen\bin\pixel-gen.bat`. Run it from the project folder: paths like `assets/` and `palettes/` resolve from there.

## Workflow

### 1. Brief

Settle these before drawing; ask the user only what you can't decide sensibly yourself:
- **What it is** and what it's for in the game (decoration, something to pick up, something to open).
- **Size**, from the table in style-guide section 1 (most objects 16×16, tall ones 16×32). Not bigger than it needs.
- **States** it needs as frames: a chest `closed` and `open`, a crop at each growth stage. Frames of one sprite share a size and an anchor, so draw states in one file.
- **Colour versions** as `variant` lines (a blue and a red chest), not separate files.
- **File:** `assets/<group>/<name>.sprite`, such as `assets/objects/chest.sprite`. The folder under `assets/` becomes the folder in Godot.

### 2. Plan

In a few lines of thinking, not in the file:
- **Silhouette:** the outline shape in the 3/4 view (top and front both show; style-guide section 2).
- **Materials and their ramps:** wood `W w V v`, stone `X x Z z`, leaves `F G g L l`, and so on.
- **Light:** which surfaces are lit (top, left), which are in shade (front-right, underside), where the cast shadow falls.
- **The anchor:** the bottom centre where it touches the ground, usually `anchor <w/2> <h-1>`; keep 1 px free around the art for the outline.
- **Room for every state:** a state that adds something (a sprout in a pot, an open lid) needs space in the same frame. Place the base so the largest state fits, with 1 px free at the top and the bottom row left for the shadow.

### 3. Draw in passes, checking each one

Write the file, then after **each** pass run `pg validate <file>` and `pg review <file>`, and **open `preview/<name>/review.png` with the Read tool**.

1. **Silhouette and flat colours:** the `K` outline and one middle shade per material. Judge the shape at 1× and 2× (the bottom row of the review image). Fix the shape before shading; shading never rescues a bad silhouette.
2. **Light and shade:** step along each ramp, lightest at the top left, darkest at the bottom right and under overhangs. Add the lit lip and the shadow row (style-guide section 3).
3. **Detail and inside lines:** inside lines in `k` or the darkest shade, never `K`. Put details on seams and edges. Short dashes for grain, no full-width dark rows.
4. **Clean-up:** go through style-guide section 6: uneven steps, doubled pixels, pillow shading, banding, stray specks.

Tips that save rounds:
- **Count widths as you write.** Every row of a frame is exactly the frame width. `validate` reports any row that isn't, with its line.
- **Symmetric things:** draw half as a `part`, then `stamp` it twice, the second time with `flipx`. Then recolour the right half one shade darker for the light.
- **Repeated pieces** (a plank, a band, a leaf cluster) are parts too.
- **States differ only where they must.** A state that only flips, shifts or recolours another is a derived frame (`frame lit from unlit recolor ...`). A state that **adds** something can't be derived, because derived frames can't stamp: make the shared base a `part`, then build each state as `stamp base 0 0` plus its own stamps. Both states then line up exactly.
- **Where one thing crosses another** (a stem over a rim, a strap over a lid), let it cross without an outline. A `K` border in the middle of the sprite breaks the "K only on the silhouette" rule. Let outlines that meet merge: a leaf's bottom outline can be the rim's top outline.
- **Colour variants map shade for shade onto one ramp**, darkest to darkest: `r>c R>b e>B E>B` turns the 4-shade red into the 4-shade water ramp. If the target ramp has fewer shades, map two shades to the same one rather than borrowing a colour from another ramp. Review each variant's image as well.
- To refer to a pixel while reviewing, use the coordinates along the review image's edges.

### 4. Review

After the last pass, go through the **self-review checklist** (style-guide section 11) item by item, against the image. For each problem, write down where it is (`row 7, x 3–5`) and the fix, then fix them all and review again. Usually 2–3 rounds are needed; stop when the checklist passes, not after a fixed count.

Also check:
- `pg validate` shows **no warnings** you can't explain.
- The sprite reads against grass and soil (`g` and `d`): its outline and lit top should separate it from both.

### 5. Show the user

Send the review image (and, if there are colour variants, the variant's review image) to the user with a short note: what you drew, the size, the frames and variants, and anything you weren't sure about. Ask for feedback before exporting if the user hasn't seen this kind of sprite yet.

### 6. Export

If the project has a `pixelgen.conf`, run `pg export`. It writes only the changed sprites into the Godot project, along with a ready-made scene per sprite. Report what it wrote.

- `pixelgen.conf` names a folder on the user's machine and is git-ignored. Never commit it, or anything from the user's Godot project.
- No `pixelgen.conf`? Skip the export and tell the user how to create one (format.md, "Settings").

## Fixing an existing sprite

Read the file and its review image first, then change only what the request asks for. Keep the frame names, sizes and anchor, since the game may depend on them, unless the user asks to change them. Then review and export as above.

## Starting from an image

If the user gives a PNG, convert it with `pg import <png> --palette farm --name <name> --out assets/<group>/<name>.sprite`, which maps every colour to the nearest palette colour and lists the ones that weren't exact. Then review the result like your own drawing. Imported art usually needs the outline and light rules applied.
