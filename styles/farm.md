# Farm style

The default style: a cozy farm in warm daylight. These rules add to the shared [style guide](../docs/style-guide.md); where they're more specific, they win.

| Setting ([farm.style](farm.style)) | Value |
|---|---|
| Palette | `palettes/farm.pal`: warm lights, cool purple-leaning shadows |
| Light | from the **top left**: lit sides top and left, shaded sides bottom and right |
| Bare `outline` | `K selective` |
| Shadow colour | `~` (33 % ink) |
| Tiles | 16 × 16 |

## Materials and their ramps

Each ramp runs dark to light. Use 3–5 shades of one ramp per material.

| Material | Ramp | Symbols |
|---|---|---|
| Outlines | ink | `K` silhouette, `k` soft (inside lines) |
| Skin | skin | `J j S s` |
| Hair | hair | `H h u` |
| Wood, bark, trunks | wood | `W w V v` |
| Soil, dirt, sand | soil | `D d T t` |
| Leaves, grass, crops' greens | leaf | `F G g L l` |
| Stone, metal | stone | `X x Z z` |
| Water, blue cloth | water | `B b c C` |
| Red cloth, carrots, apples | red | `E e R r` |
| Gold, straw, yellow flowers | gold | `O o Y y` |
| Violet cloth, lavender | violet | `M m N n` |
| Cream, white cloth | light | `Q q` |

**Accents** are small spots from the red, gold and violet ramps: a gold lock, a red blush (`r`), a flower.

## Outlines

- **Characters:** full `K` outline (`outline K`, or drawn by hand), so they read on grass and soil alike.
- **Organic shapes** (trees, bushes, crops, plants): `outline` (bare) or `outline K selective`. The lit top-left edges take the darkest shade of the leaf or wood next to them.
- **Wooden and stone objects** (chests, crates, fences): a `K` silhouette, drawn by hand or with `outline K`; inside lines (lid seams, planks) in `k` or `W`.

## Shadows

The `shadow` effect in `~`, under the anchor:

| Thing | Shadow |
|---|---|
| Character (16 × 32) | `shadow 12 3` |
| Small object (16 × 16) | `shadow 14 3` (the chest) |
| Small tree (32 × 48) | `shadow 18 4` |

## Ground and contrast

Most of the screen is **grass** (`g` with specks of `G` and `L`) and **soil** (`d` with `D` and `T`). Things that stand on them need to separate from both: a `K` outline, a lit top, or a ramp that isn't green or brown.

**Grass meeting dirt** (`assets/tiles/ground.tileset`): grass is the raised ground, with `bank G` (grass with dirt below or right of it), `lip L` (grass with dirt above or left of it) and `shadow D` (dirt just below or right of grass).

## Colour variants

Clothes and hair are variants that map shade for shade onto one ramp, darkest to darkest: the farmer's shirt `R>L e>g E>G` (green), or `R>N e>m E>M` (violet) with hair `u>Y h>o H>O`.
