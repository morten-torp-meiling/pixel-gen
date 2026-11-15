# pixel-gen file formats

Reference for the three text formats pixel-gen reads and the one settings file:

| File | Holds | Lives in |
|---|---|---|
| `.pal` | a palette: every colour a sprite may use, grouped into ramps | `palettes/` |
| `.sprite` | a sprite: parts, frames, animations and recoloured variants | `assets/**` |
| `.tileset` | tiles for a Godot TileMapLayer, in the sprite format | `assets/**` |
| `.mockup` | a small map of a tileset's tiles, to check how they fit together | next to the tileset |
| `pixelgen.conf` | where `export` puts the files in the Godot project | project folder (git-ignored) |

How to draw well in these formats is in [style-guide.md](style-guide.md); this file is only about what the tool accepts.

## Rules shared by all formats

- **One statement per line.** Spaces and tabs separate words; leading indentation is ignored, so grids can be indented.
- **Comments** are whole lines whose first non-space character is `#`. They may appear anywhere, also between grid rows. There are no comments at the end of a line: `frame idle  # note` is an error.
- **Names** (sprites, parts, frames, tiles, animations, variants) use letters, digits, `_` and `-`.
- **Every error is reported at once**, sorted by line, as `file:line: message`. Warnings (things that are allowed but probably unintended) are reported the same way and don't stop anything.
- Files are UTF-8; pixel symbols are single ASCII characters.

## Palettes (`.pal`)

```
# Farm palette: every ramp goes from dark to light.
palette farm

ramp leaf
  F  #1f3d32  leaf deepest
  G  #2f6140  leaf dark
  g  #4a8a3e  leaf
  L  #7db547  leaf light

ramp effects unordered
  ~  #1c152055  shadow
```

| Line | Meaning |
|---|---|
| `palette <name>` | Optional; the name defaults to the file name. |
| `ramp <name>` | Starts a ramp. The colours below it belong to it, **darkest first**. |
| `ramp <name> unordered` | A ramp whose colours aren't shades of one colour (accents, effects): no dark-to-light check. |
| `<symbol>  #rrggbb  [name ...]` | A colour. The name is free text, for people. |
| `<symbol>  #rrggbbaa  [name ...]` | A translucent colour; `aa` is the opacity, `01` to `fe`. |

- A **symbol** is one printable ASCII character other than `.` (always transparent) and `#` (comments). Each symbol is used once per palette.
- Every colour belongs to a ramp.
- Checks: duplicate symbols and bad hex are errors; alpha `00` is an error (use `.` for no pixel). It's a warning when a colour repeats another, when an ordered ramp doesn't get lighter step by step, when two neighbouring shades are hard to tell apart (lightness step under 0.04 in OKLab), and when a translucent colour sits in an ordered ramp.
- **Ramps matter beyond grouping:** `outline <colour> selective` (below) uses the darkest shade of an ordered ramp, so keep ramps true dark-to-light runs of one hue.

**Finding a palette.** A sprite's `palette farm` loads `palettes/farm.pal`. A value that ends in `.pal` or contains `/` is a path from the project folder instead.

**Tools.**
- `pg palette farm` checks the palette, lists it and writes `preview/palette-farm.png`, a swatch to judge the ramps by eye.
- `pg palette import file.hex name` turns a Lospec `.hex` palette into `palettes/name.pal`, with symbols assigned in order and every colour in one unordered ramp. Sort the colours into ramps by hand afterwards.

## Sprites (`.sprite`)

A sprite is a set of equally sized **frames** (finished images), built from optional reusable **parts**, with optional **animations** over the frames and recoloured **variants** of the whole thing.

### A complete example

```
# A mushroom that bobs; a blue variant comes free.
sprite mushroom
size 8 8
palette farm
anchor 4 7

part cap
  .eeee.
  eRRRee
  eeeeEE
end

part stem
  qQ
  qQ
end

frame idle
  stamp cap 1 1
  stamp stem 3 4
end

frame dip from idle shift 0 1

anim bob fps 4 loop: idle*3 dip

outline K selective
shadow ~ 6 2

variant mushroom_blue recolor e>b R>c E>B
```

### Header

| Line | Meaning |
|---|---|
| `sprite <name>` | Optional; defaults to the file name (`farmer.sprite` is `farmer`). |
| `size <width> <height>` | **Required.** Frame size in pixels, 1 to 256 each. Characters are `16 32`, objects `16 16` or what they need. |
| `palette <name or file.pal>` | **Required.** Where the symbols come from. |
| `anchor <x> <y>` | The pivot, such as a character's feet: Godot puts this pixel on the node's position, and `shadow` centres on it. Defaults to the bottom centre, `width/2, height-1`. Must lie inside the frame. |

Header lines may each appear once, anywhere in the file; the file is read whole before it's checked, so the order of statements doesn't matter (except inside a block).

### Grids

A grid is rows of symbols from the palette, one character per pixel, `.` for transparent. Rows can't contain spaces.

```
  ..KKKK..
  .KggggK.
```

### Parts

```
part <name>
  <rows>
end
```

A part is a reusable piece of any size (all its rows equally wide). Parts can't contain `stamp`, `recolor`, `outline` or `shadow` lines.

### Frames

```
frame <name>
  <rows>                         optional: exactly 'size' rows of 'size' width; otherwise the frame starts empty
  stamp <part> <x> <y> [flipx] [flipy]
  recolor <from>><to> ...
  outline <colour> [outer|selective]
  shadow <colour> <width> <height>
end
```

Rows come first, then any number of operations, applied in the order written:

- **`stamp <part> <x> <y> [flipx] [flipy]`** paints a part with its top-left corner at `x, y` (0, 0 is the frame's top-left), mirrored if asked. Transparent pixels of the part leave the frame as it was, so later stamps go on top. A stamp may hang over the frame edge; what falls outside is cut off, with a warning.
- **`recolor <from>><to> ...`** swaps symbols in the image so far, all at once: `recolor a>b b>a` swaps two colours. `<to>` may be `.` to erase a colour; `<from>` can't be `.`.
- **`outline <colour> [outer|selective]`** adds a 1 px outline around every solid shape, on its four sides (not diagonally, which keeps corners round). It only paints empty or translucent pixels, never the art.
  - `outer` (the default): every outline pixel is `<colour>`.
  - `selective`: on the lit top and left sides, the outline takes the darkest shade of the ramp of the pixel it borders (a leaf gets a dark green edge); elsewhere `<colour>`. Matches light from the top left.
  - An outline that would fall outside the frame is cut off, with a warning: leave 1 px free around the art.
- **`shadow <colour> <width> <height>`** draws a filled ellipse, centred on the anchor's x, with its bottom row on the anchor's row. It only fills empty pixels, so it sits behind the art. Use a translucent colour (`~` in `farm`). For characters, keep the bottom row of the frame free (feet on row 30 of 32, anchor `8 31`) so the shadow shows below the feet.

### Derived frames

```
frame <name> from <frame> [flipx] [flipy] [shift <dx> <dy>] [recolor <from>><to> ...]
```

A frame made from another one, on a single line, with no rows and no `end`. The options are applied in the order written. `shift` moves the image (pixels pushed out of the frame are cut off, with a warning). Derived frames can derive from derived frames, in any order in the file; loops are errors.

```
frame left_0 from right_0 flipx
frame bob from idle shift 0 -1
frame idle_red from idle recolor e>R
```

Flipping mirrors the lighting too (left-facing frames end up lit from the top right); see the style guide for when that matters.

### Effects for every frame

`outline` and `shadow` lines outside any frame apply to every frame, after the frame's own lines and after derivation, in the order written. A flipped frame therefore gets its outline and shadow drawn fresh, on the correct side.

### Animations

```
anim <name> fps <n> loop|once: <frame>[*<duration>] ...
```

- `fps`: ticks per second, above 0 and at most 120; may be a decimal.
- `loop:` repeats; `once:` stops on the last frame.
- Each step shows a frame for one tick, or `*<duration>` ticks (a decimal, like `*1.5`). A frame may appear any number of times.

```
anim walk_down fps 8 loop: down_1 down_0 down_2 down_0
anim swing fps 10 once: swing_0 swing_1*2 swing_2
```

### Variants

```
variant <name> recolor <from>><to> ...
```

A recoloured copy of the whole sprite (every part and frame, same animations), exported as a sprite of its own. The name must differ from the sprite's.

### Checks

Errors: missing `size` or `palette`, rows of the wrong width or count, symbols not in the palette (the first 10 per grid are listed), unknown parts or frames, duplicate names, a frame with no rows and no stamps, rows after an operation, a block without `end`. Warnings: a frame or part with no visible pixels, stamps and effects cut off at the frame edge.

## Tilesets (`.tileset`)

The sprite format with **tiles** in place of frames. Parts, `stamp`, `recolor`, `outline`, derived tiles (`tile b from a flipx`) and `variant` all work as in sprites.

### A complete example

```
# Shrunk to 4x4 tiles to keep it short; real tiles are 16x16, the default.
tileset ground
size 4 4
palette farm
columns 3

tile grass
  gGgg
  ggGg
  Gggg
  gggG
end

tile grass_b from grass flipx

# Grass on top, soil below: repeats sideways only.
tile grass_soil wrap x
  gggg
  GGGG
  DDDD
  dddd
end

variant ground_autumn recolor g>o G>O
```

### What differs from sprites

| Line | Meaning |
|---|---|
| `tileset <name>` | In place of `sprite`; optional, defaults to the file name. |
| `size <w> <h>` | Optional; **16 16** unless set. |
| `columns <n>` | Optional: tiles per row in the atlas image (1 to 256). Defaults to the tile count, at most 8. |
| `tile <name> [wrap both\|x\|y\|none]` | In place of `frame`. `wrap` says which edges must meet when the tile repeats: `both` (the default, plain ground), `x` (left and right only: the top edge of a path), `y` (top and bottom only), `none` (corners, single pieces). |
| `anim`, `anchor`, `sprite`, `frame` | Not allowed. Tiles don't animate (yet) and sit on the grid. |

`wrap` also works on derived tiles: `tile path_s from path_n flipy wrap x`.

### The seam check

`pg tilecheck` checks every edge pair a tile's `wrap` asks for. It compares how much neighbouring pixels differ **across** the seam with how much they differ **inside** the tile, so a textured tile isn't flagged for its texture. A seam fails when it differs more than twice as much as the inside, plus a small allowance (so a stray speck on an edge passes). It reports the rows or columns that don't match and draws `preview/<tileset>/tilecheck.png`, with each tile repeated 4×4 and failing seams marked in red.

## Mock maps (`.mockup`)

A small map made of a tileset's tiles, drawn by `pg mockup <file.tileset> <file.mockup>` to `preview/<tileset>/mockup-<name>.png`: enlarged with no lines between tiles (so seams show as in the game), and again at 1× and 2×. It's for checking that different tiles fit next to each other, which `tilecheck` can't see. Mock maps are never exported.

```
# A dirt patch in grass, with an inner corner.
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

- **Legend lines** `<character> = <tile> [<tile> ...]` give a map character its tile. With several tiles, each cell picks one of them, the same way every time: that's how to see base-tile versions mixed.
- **`map`** starts the map; every following line is a row, one character per tile, all rows equally wide (at most 64 × 64). `.` leaves a cell empty.
- Options: `--variant <name>` draws a recoloured variant of the tileset; `--grid` adds faint lines between tiles; `--scale N` (default 4).

## Settings (`pixelgen.conf`)

Read by `pg export` and `gradlew exportAssets`. It names a folder on one machine, so it's git-ignored: everyone writes their own.

```
# The Godot project folder (the one with project.godot); relative paths start here.
godot.project = ../farm-game
# Folder in the Godot project that receives the art. Optional; this is the default.
godot.art = res://art
# Also write a ready-made AnimatedSprite2D scene per sprite. Optional; on by default.
scenes = true
```

## What the tool makes from these files

| Command | Reads | Writes |
|---|---|---|
| `pg validate [file\|folder ...]` | sprites, tilesets | report only; default `assets/` |
| `pg render <...> [--scale N] [--frame f]` | sprites, tilesets | `preview/<sprite>/<frame>.png` |
| `pg review <...> [--strip] [--onion]` | sprites, tilesets | `preview/<sprite>/review.png` (or `strip.png`, `onion.png`) |
| `pg gif <...> [--anim a] [--scale N] [--bg #rrggbb\|none]` | sprites | `preview/<sprite>/<anim>.gif` |
| `pg sheet <...> [--padding N] [--extrude N] [--list]` | sprites, tilesets | `preview/<sprite>/sheet.png` |
| `pg tilecheck <...>` | tilesets | `preview/<tileset>/tilecheck.png`; exit 1 on a seam |
| `pg mockup <file.tileset> <file.mockup> [--variant v] [--grid]` | a tileset and a mock map | `preview/<tileset>/mockup-<name>.png` |
| `pg frames <...> [--res res://dir]` | sprites | `preview/godot/<sprite>.png` and `.tres`, to copy by hand |
| `pg tiles <...> [--res res://dir]` | tilesets | `preview/godot/<tileset>.png` and `.tres`, to copy by hand |
| `pg import <png> --palette p` | a PNG | `.sprite` text, nearest palette colours |
| `pg export [--all]` | everything under `assets/` | the Godot project (below) |

`pg` is the launcher `build/install/pixel-gen/bin/pixel-gen` (`gradlew installDist` makes it). Every command prints its usage with `pg help`.

### In the Godot project

`pg export` writes into `godot.art`, keeping the folders under `assets/`: `assets/characters/farmer.sprite` goes to `res://art/characters/`.

- **A sprite** and each variant become:
  - `<name>.png`: the sheet. One row per animation (frames in play order, each frame stored once), then a row of frames no animation uses; 1 px between frames.
  - `<name>.tres`: a `SpriteFrames` resource. Each animation keeps its name, `fps` (as speed), looping and per-frame durations. Each frame no animation uses becomes a one-frame animation named after the frame, so still sprites can be switched with `play("open")`.
  - `<name>.tscn` (when `scenes = true`): an AnimatedSprite2D using those frames, with the texture filter on Nearest, Centered off and Offset at minus the anchor (so the anchor sits on the node's position), and the first animation set to autoplay. Autoplay starts when the game runs, not in the editor. The export rewrites this file: put it inside your own scenes rather than editing it.
- **A tileset** and each variant become `<name>.png` (the atlas, tiles in file order, `columns` per row) and `<name>.tres`, a `TileSet` whose tiles are ready to paint. A custom data layer `name` holds each tile's name: `tile_data.get_custom_data("name")`.
- Only sources that changed since the last export are written again (their text, their palette's colours or the settings), plus any whose files were deleted from the Godot project. `--all` writes everything.
- Files the export wrote before but no longer writes (a deleted sprite or variant) are removed, with their `.import` files. Nothing else in the Godot project is touched.
- Godot's uid in a `.tres` or `.tscn` header is kept when the file is overwritten, so scenes that use it keep working.
- Set the texture filter to **Nearest** on nodes you set up by hand (or project-wide under Rendering > Textures > Default Texture Filter); the generated scenes already have it.
