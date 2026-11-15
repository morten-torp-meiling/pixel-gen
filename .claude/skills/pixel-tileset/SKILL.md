---
name: pixel-tileset
description: Draw ground tiles for the Godot game (grass, soil, sand, stone, water, paths, tilled earth) as a .tileset file, with seamless repeats, varied versions and transition edges and corners between two kinds of ground; check them with tilecheck and export a Godot TileSet. Use whenever the user asks in this project for tiles, ground, terrain, a tileset, or a transition such as grass to dirt. For objects that stand on the ground use pixel-sprite.
---

# Drawing ground tiles

Tiles are the background the whole game sits on, so they follow different priorities from sprites: **quiet, seamless, and varied enough that the grid disappears.** They live in a `.tileset` file (the sprite format with `tile` in place of `frame`; see `docs/format.md`, "Tilesets").

Read before drawing (once per session):
- `docs/style-guide.md`: section 10 (tiles), plus 3 (light) and 4 (palette).
- The `pixel-sprite` skill for the tool setup and the drawing passes. Run `./gradlew installDist -q` first; `pg` below means `build/install/pixel-gen/bin/pixel-gen`.

## 1. Plan the tileset

- **One `.tileset` file per TileMapLayer.** A Godot TileMapLayer paints from one TileSet, and each file exports as one. Ground that's painted together (grass, dirt, and the edges between them) goes in one file: `assets/tiles/ground.tileset`.
- **Base tiles:** one per kind of ground, plus **2–4 versions of each** that's painted over large areas (grass, dirt).
- **Transitions:** for each pair of grounds that meet (dirt patches in grass), a set of 12: 4 edges, 4 outer corners, 4 inner corners (below).
- **Size:** 16 × 16 (the default; leave out `size`).

## 2. Base tiles

1. **Draw the base tile** in one ramp: the middle shade as the ground, texture in the neighbouring shades. Small clusters of 2–3 pixels (a tuft, a pebble), evenly spread. No outlines, nothing that pops out.
2. **Make it seamless.** Texture may cross an edge if it continues on the opposite edge: a tuft split over the right column and the left column, or the bottom row and the top row.
3. **Check:** `pg tilecheck <file>`, then open `preview/<tileset>/tilecheck.png`. Every seam must pass, and the 4 × 4 repeat must look even, with no lines, empty patches or clumps.
4. **Versions:** `tile grass_b from grass flipx` costs nothing, and `flipy` or both give two more. Flips keep the texture but move it around; for a version that really differs, redraw the clusters in new places (keeping the edges seamless). Avoid one feature that's unique to one tile (a big flower): at every repeat it becomes a pattern.

In Godot, select all the versions of a base tile in the TileMap panel and turn on random painting (the dice button) to scatter them.

## 3. Transitions between two grounds

Think of one ground as the **patch** (dirt) lying in the other, the **surround** (grass). Name tiles by where they sit on the patch, and draw them on a 3 × 3 picture of a patch:

```
corner_nw   edge_n    corner_ne        inner_nw  inner_ne
edge_w      (dirt)    edge_e           inner_sw  inner_se
corner_sw   edge_s    corner_se
```

| Tiles | Example | What's in it | `wrap` |
|---|---|---|---|
| `dirt_edge_n/s` | `dirt_edge_n` | grass above, dirt below | `x` |
| `dirt_edge_w/e` | `dirt_edge_w` | grass left, dirt right | `y` |
| `dirt_corner_nw/ne/sw/se` | `dirt_corner_nw` | grass on top and left, dirt in the bottom-right | `none` |
| `dirt_inner_nw/ne/sw/se` | `dirt_inner_nw` | dirt, except a grass corner in the top-left | `none` |

Order the file so the atlas reads like this picture: with `columns 4`, the rows `corner_nw edge_n corner_ne inner_nw`, `edge_w dirt edge_e inner_ne`, `corner_sw edge_s corner_se inner_sw`, `grass grass_b grass_c inner_se`. That makes picking tiles in Godot easy.

**How to draw them:**
- **Textures are parts.** Put each base texture in a part (`part grass_tex`, `part dirt_tex`) and make the base tile just `stamp grass_tex 0 0`. Transition tiles stamp the same parts, so their texture matches the base tiles exactly where they meet.
- **Define the boundary once, as overlay parts,** and build every piece from them:
  - `grass_top`: grass texture over the top rows, the wobbly boundary, then the shadow row, with `.` below it. Full width.
  - `grass_left`: the same boundary turned sideways. Full height.
  - `grass_nub`: a small grass corner for the inner corners.
  - `dirt_edge_n` = `stamp dirt_tex 0 0`, `stamp grass_top 0 0`. `dirt_corner_nw` = dirt, then `grass_left`, then `grass_top`. `dirt_inner_nw` = dirt, then `grass_nub`.
  - The other directions are the same parts with `flipx`/`flipy` stamps, then the light fixed (below).
  - Because every piece uses the same boundary, edges, corners and inner corners all meet at the same points.
- **The boundary wobbles,** 1–2 px in and out, never a ruler-straight line. Where it reaches the tile's sides it must be at the **same depth on both sides** (for example 4 px in at both the left and right ends of `grass_top`): that's what lets a tile sit next to a copy of itself, and next to the corners. A shape this systematic may be written by a small script (a depth per column), as long as the result is reviewed like any drawing.
- **Light, one surface higher than the other.** Grass stands slightly above dirt. Where the grass edge is on the top or left of the dirt, the grass casts a 1 px dark row onto the dirt (`D` along the boundary). Where it's on the bottom or right, the grass edge gets a lit row instead (`L`). A flipped tile flips this too: derive shapes with `flipx`/`flipy` if you like, but then fix the light rows.
- **Check every transition tile with tilecheck** (it checks the wrap each one declares), and review the whole set with `pg review <file>`.

## 4. Check the set

- `pg validate <file>`: no errors, no unexplained warnings.
- `pg tilecheck <file>`: all seams pass. Open the image: no grid lines, no patterns.
- `pg review <file>`: every tile at 1× and 2× in the actual-size row. Ground should read as ground, quietly.
- **Neighbours:** tilecheck can't see two different tiles next to each other; a **mock map** can. Write `assets/tiles/<name>.mockup` next to the tileset (format in `docs/format.md`, "Mock maps"): a legend of one character per tile (several tiles to scatter them), then a small map. Draw a patch with every kind of piece in it, including an inner corner, and a long stretch of edge:

  ```
  g = grass grass_b grass_c
  1 = dirt_corner_nw
  n = dirt_edge_n
  w = dirt_edge_w
  d = dirt
  i = dirt_inner_nw
  map
    gggggggg
    gggg1nnn
    ggggwddd
    1nnniddd
    wddddddd
  ```

  `pg mockup assets/tiles/ground.tileset assets/tiles/patch.mockup`, then open `preview/ground/mockup-patch.png`. Follow the boundary across every tile join: no breaks, no jumps in depth, the shadow row continuous. Check the actual-size row too: at 1× the grid of base tiles must disappear. Keep the `.mockup` files: after any change to the tiles, run them again.
- **Long edges repeat too.** Along a straight run of `edge_n` the same wobble comes back every 16 px. Give edges that run long 2 versions with a different wobble (same depth at the tile's sides), and scatter them in the mock-up (`n = dirt_edge_n dirt_edge_n_b`).

## 5. Show and export

Send the user the tilecheck, mock-up and review images, listing the tiles, which ones repeat which way, and the order they're in. Then `pg export` (if `pixelgen.conf` exists). The TileSet has every tile ready to paint, and each tile's name is in its `name` custom data (`tile_data.get_custom_data("name")`). The export doesn't set up Godot terrains (automatic transitions) yet, so transitions are painted by picking the tiles; say so if the user expects autotiling. Never commit `pixelgen.conf` or anything from the user's Godot project.
