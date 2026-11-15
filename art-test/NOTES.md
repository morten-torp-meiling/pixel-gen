# Art test: lessons for the style guide (step 17)

This test covered a chest (16×16) and a farmer (16×32, facing down), each through three rounds of drawing, review and fixing. Each round's review sheet is in `rounds/`.

## What fixed the most
- **Black only for the silhouette.** Lines inside a shape, such as between an arm and the torso, use the soft outline `k` or the darkest shade of the material. Black lines inside a shape look heavy.
- **No full-width dark rows.** They read as stripes. Use short 2–3 px dashes for wood grain and similar texture, offset from row to row.
- **Volume comes from overlap and wrap-around:**
  - a lid that is 1 px wider than the body;
  - bands that continue over the top surface;
  - a lit lip where a top surface meets a front surface;
  - a shadow row right under an overhang.
- **Light from the top left:**
  - left column one shade lighter, right column one shade darker;
  - top surfaces lightest, front surfaces middle, undersides darkest.
- **Locks and details sit on seams**, such as a plate across the lid line with a 1 px keyhole.
- **Faces at 16 px wide:**
  - eyes are 1×2 dark pixels, 4 px apart, centred;
  - one blush pixel (`r`) under each eye, a 2 px mouth in the skin shadow colour;
  - short side hair, so the face is at least 10 px wide;
  - the fringe is 2–3 clumps pointing down, with skin showing between them.
- **A collar or neckline** breaks the line between head and body.

## Process
- Judge at 1× and 2×, using the actual-size row on the review sheet, not only the enlarged view.
- Counting pixels in text rows is error-prone. `validate` caught every width or height mistake.
- Front-facing characters are almost symmetric. A way to draw half and mirror it (then fix the lighting) would save time and errors. It could be a `mirror` helper, or parts stamped with `flipx`.

## Weak spots that remain
- Organic shapes (hair, faces, hands) come out stiff and blocky.
- No anti-aliasing or subtle colour shifts within a material. The shading is clean but plain.
- Every pose is symmetric and static. Animation (step 10 onwards) will show whether it holds up in motion.
