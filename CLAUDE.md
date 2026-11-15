# pixel-gen

A toolchain for drawing pixel art for a Godot 4 game: a cozy top-down 3/4 view on a 16 px grid, 16 × 32 characters, all assets original. **Claude draws** the sprites as text grids (`.sprite`, `.tileset`); the tool checks them, renders review images, and exports sheets, SpriteFrames, TileSets and scenes into the Godot project.

## Drawing art: use the skills

For any request to draw, animate or fix art, load the matching skill first; it holds the workflow and the review loop:

| Request | Skill |
|---|---|
| A still sprite: object, item, crop, prop, one character pose | `pixel-sprite` |
| An animation: walk, idle, swing, more directions, a looping effect | `pixel-animate` |
| Ground tiles, terrain, transitions between grounds | `pixel-tileset` |

The rules they apply are in [docs/style-guide.md](docs/style-guide.md) (how the art should look, with the self-review checklist) and [docs/format.md](docs/format.md) (the file formats and what every command writes).

## Commands

```bash
./gradlew build installDist        # compile, run all tests, refresh the launcher
./gradlew installDist -q           # refresh the launcher only; run this at the start of every session
./gradlew test --tests "*Tileset*" # selected tests
./gradlew exportAssets             # export assets/ into the Godot project (UP-TO-DATE when nothing changed)
```

The launcher (`pg` in the docs) is `build/install/pixel-gen/bin/pixel-gen` from Git Bash, `build\install\pixel-gen\bin\pixel-gen.bat` from PowerShell. It only changes when `installDist` runs, so **run `./gradlew build installDist` after every code change**, or you'll be testing the old tool. Run it from the project folder: `assets/`, `palettes/` and `preview/` resolve from the current folder. `pg help` lists every command.

The main ones: `validate`, `review [--strip|--onion]`, `gif`, `tilecheck`, `mockup`, `export [--all]`. Everything else is in format.md's command table.

## Layout

| Path | What |
|---|---|
| `src/main/java/pixelgen/` | `Cli` (entry point, command registry); `cli/` (one class per command); `parse/` (palette, sprite, tileset, mockup parsers); `model/` (Grid, Palette, Sprite, Tileset, Anim, Effects, SeamCheck, Colors); `render/` (renderers, review sheets, GIF, sheet packing); `godot/` (SpriteFrames, TileSet and scene writers, the exporter) |
| `src/test/java/pixelgen/` | JUnit 6 tests, mirroring the main packages |
| `src/test/resources/` | sample palettes, sprites, tilesets, and `golden/` (hand-written expected `.tres`/`.tscn`) |
| `palettes/` | `farm.pal`, the game palette |
| `assets/` | the game's art sources; folders here become folders under `res://art/` in Godot |
| `docs/` | `format.md`, `style-guide.md` |
| `.claude/skills/` | `pixel-sprite`, `pixel-animate`, `pixel-tileset` |
| `art-test/` | the early chest and farmer art test, its review rounds, and `NOTES.md` with the lessons that shaped the style guide |
| `preview/` | review images and GIFs (git-ignored) |
| `pixelgen.conf` | where `export` writes: the Godot project folder (git-ignored; see format.md, "Settings") |

## Code conventions

- **Java 21, no runtime dependencies.** The JDK's `BufferedImage` and `ImageIO` cover PNG and GIF; the text formats are our own. Raise it with the user before adding a library.
- **Commands** implement `Cli.Command` (`usage`, `summary`, `run(args, out, err)`) and are registered in `Cli`. Exit codes: 0 ok, 1 the command failed (bad input file, failed check), 2 usage error.
- **Parsers report every error at once**, as `file:line: message`, sorted by line (`SpriteParser.sortByLine`), thrown together in a `ParseException`. Messages say what's wrong and what to write instead. Warnings are returned with the result, not thrown.
- **Godot files are deterministic** (no random ids), so they can be compared with golden files; the exporter keeps the uid Godot adds to a header.

## Tests

- Every feature gets tests; `./gradlew build` must pass before a step is done.
- Tests that write files use JUnit's `@TempDir`, never the project folder. Copy what they need from `src/test/resources/` into the temp folder and pass that folder to the command as its project folder.
- **Golden files** in `src/test/resources/golden/` are written by hand, as Godot itself would write them. Compare after `.replace("\r\n", "\n")`: git may check them out with Windows line endings.
- Command output on Windows ends lines with `\r\n`: compare output line by line (`out.lines()`), not against strings with `\n`.

## Working with this user

- **Commit only when asked**, and stage files by name.
- **Never commit `pixelgen.conf`, anything from the user's Godot project, or demo art.** `pixelgen.conf` is git-ignored. Demo files may sit in `assets/` while the real assets are being made: check `git status` before every commit.
- Paths in `pixelgen.conf` must be Windows paths (`C:/...` or relative), not Git Bash paths (`/c/...`): the tool is a Windows Java program.
