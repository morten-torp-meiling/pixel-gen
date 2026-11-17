# Cave style

Caves and mines: cool, dark rock and earth, old timber and iron, lit by lanterns. These rules add to the shared [style guide](../docs/style-guide.md); where they're more specific, they win.

| Setting ([cave.style](cave.style)) | Value |
|---|---|
| Palette | `palettes/cave.pal`: dark, blue-violet rock and reddish earth; warm only in the lantern ramp |
| Light | from the **top right** (the lantern): lit sides top and right, shaded sides bottom and left |
| Bare `outline` | `K outer` |
| Shadow colour | `~` (40 % ink, darker than the farm's) |
| Tiles | 16 × 16 |

## Materials and their ramps

| Material | Ramp | Symbols |
|---|---|---|
| Outlines | ink | `K` silhouette, `k` soft (inside lines) |
| Rock, walls, boulders | stone | `X x Z z A` |
| Cave floor, earth, gravel | floor | `D d T t` |
| Mine timbers, crates, ladders | timber | `W w V v` |
| Rails, carts, lantern frames, tools | iron | `I i J j` |
| Flames, embers, lamplight | lantern | `O o Y y q` |
| Crystals, ore veins | crystal | `C c N n` |

The cave palette has no skin, hair or cloth. **Characters keep their own style** (the farmer is a farm sprite wherever they walk); a character that only lives underground needs those ramps added to the palette first.

## Light

- **The light comes from the top right,** as if a lantern hung up and to the right of everything. Shade every surface that way: tops lightest, right edges one shade lighter, left edges and undersides darker. This is the mirror of the farm, so a farm habit (lit left edge) is a mistake here.
- **Values are low.** Most of a cave sits in the bottom half of each ramp: ground in `D d T`, rock in `X x Z`. Keep the top shades (`t`, `z`, `A`, `v`, `j`) for lit tops, edges and highlights, in small clusters, so objects read against the dark.
- **Warm colour only on and right next to light sources.** A lantern's glass and flame use the lantern ramp (`O o Y y q`, with `q` at the core). Surfaces right next to a flame may take one or two pixels of `Y` or `y` on their lit edge, never more. Everything else stays cool.
- **Crystals glow a little:** one `n` glint on the lit side, `N` around it. Like other accents, keep them small.

## Outlines

- **Everything that stands up gets a full `K` outline:** characters, props, rocks, timber. A bare `outline` line draws it (`K outer`).
- **Selective outlines aren't used.** The darkest shades of the cave ramps are almost as dark as `K`, so a selective edge barely shows and loses the silhouette.
- Inside lines (planks, rock cracks, a cart's panels) in `k` or the material's darkest shade.

## Shadows

The `shadow` effect in `~`, same sizes as the farm: `shadow 12 3` for a character, `shadow 14 3` for a 16 × 16 object.

## Ground and contrast

The main grounds are the **cave floor** (`d` with specks of `D` and `T`) and **gravel** (`T` with `t` and `d`). They are dark and low in contrast, so:

- Things on the floor separate by their `K` outline and a lit top or right edge from the top shades of their ramp.
- **Rock meeting floor** (a raised rock shelf, a wall's foot) is a `terrain` with the rock as raised ground: `bank X` (rock with floor below or left of it), `lip z` (rock with floor above or right of it), `shadow D` (floor just below or left of rock). The style's light places them; write the same symbols as for any terrain.

## Flipped frames

`flipx` mirrors the light, so a flipped frame is lit from the top left, which is wrong here. Check flipped frames: where a lit right edge has become a lit left edge and it shows, recolour the derived frames or draw them.
