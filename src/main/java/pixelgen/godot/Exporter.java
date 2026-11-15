package pixelgen.godot;

import pixelgen.model.Palette;
import pixelgen.model.SeamCheck;
import pixelgen.model.Sprite;
import pixelgen.model.Tileset;
import pixelgen.parse.PaletteResolver;
import pixelgen.parse.ParseException;
import pixelgen.parse.SpriteParser;
import pixelgen.parse.TilesetParser;
import pixelgen.render.SheetPacker;

import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Exports one source file into the Godot project.
 * <ul>
 *   <li>A sprite (and each variant) becomes {@code <name>.png} (the sheet), {@code <name>.tres}
 *       (SpriteFrames) and, when scenes are on, {@code <name>.tscn}.</li>
 *   <li>A tileset (and each variant) becomes {@code <name>.png} (the atlas) and {@code <name>.tres}
 *       (TileSet).</li>
 *   <li>Files go to the art folder plus the source's folder under {@code assets/}:
 *       {@code assets/characters/farmer.sprite} goes to {@code res://art/characters/}.</li>
 * </ul>
 */
public final class Exporter {

    /** Bump when the exported files change for the same source, so everything is exported again. */
    public static final int FORMAT = 1;

    /** Sprite sheets keep 1 px between frames; tile atlases need none (Godot pads tiles itself). */
    public static final int SPRITE_PADDING = 1;

    private Exporter() {}

    /** A source file read and checked, ready to export. */
    public record Source(String path, String folder, Sprite sprite, Tileset tileset, String hash) {
        boolean isTileset() { return tileset != null; }
    }

    /**
     * What one export wrote.
     *
     * @param names    the sprite or tileset names written (the source's, then each variant's)
     * @param outputs  every file written, relative to the Godot project, with forward slashes
     * @param warnings problems worth a look that don't stop the export
     */
    public record Result(List<String> names, List<String> outputs, List<String> warnings) {}

    /**
     * Parses a source and computes its hash, which covers everything its output depends on: the
     * text, the palette's colours, the export settings and {@link #FORMAT}.
     *
     * @param path   project-relative path, such as {@code assets/characters/farmer.sprite}
     * @param folder its folder below {@code assets/}, such as {@code characters} (empty at the top)
     */
    public static Source read(Path file, String path, String folder, PaletteResolver palettes, ExportConfig config)
            throws IOException, ParseException {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        String name = file.getFileName().toString();
        Sprite sprite;
        Tileset tileset = null;
        if (name.endsWith(".tileset")) {
            tileset = TilesetParser.parse(text, path, name.substring(0, name.length() - ".tileset".length()), palettes);
            sprite = tileset.sprite();
        } else {
            sprite = SpriteParser.parse(text, path, name.substring(0, name.length() - ".sprite".length()), palettes);
        }
        StringBuilder key = new StringBuilder();
        key.append("format ").append(FORMAT).append('\n')
                .append("art ").append(config.art()).append('\n')
                .append("scenes ").append(config.scenes()).append('\n')
                .append("path ").append(path).append('\n');
        Palette p = sprite.palette();
        key.append("palette ").append(p.name()).append('\n');
        for (Palette.Entry e : p.entries()) key.append(e.symbol()).append(' ').append(e.hex()).append('\n');
        key.append(text);
        return new Source(path, folder, sprite, tileset, sha256(key.toString()));
    }

    /** Writes the source's files. Files that are already there are overwritten, keeping Godot's uids. */
    public static Result export(Source src, ExportConfig config) throws IOException {
        String resDir = src.folder().isEmpty() ? config.art() : join(config.art(), src.folder());
        Path dir = config.toDisk(resDir);
        Files.createDirectories(dir);
        List<String> names = new ArrayList<>(), outputs = new ArrayList<>(), warnings = new ArrayList<>();
        String relDir = resDir.substring("res://".length());
        String prefix = relDir.isEmpty() ? "" : relDir + "/";

        if (src.isTileset()) {
            Tileset t = src.tileset();
            SheetPacker.Layout layout = SheetPacker.grid(t.sprite(), t.columns(), 0, 0);
            for (Tileset ts : t.withVariants()) {
                String png = ts.name() + ".png", tres = ts.name() + ".tres";
                writePng(dir.resolve(png), SheetPacker.render(ts.sprite(), layout));
                writeGodotText(dir.resolve(tres), TileSetWriter.write(ts, layout, join(resDir, png)));
                names.add(ts.name());
                outputs.add(prefix + png);
                outputs.add(prefix + tres);
            }
            long bad = SeamCheck.check(t).stream().filter(s -> !s.ok()).count();
            if (bad > 0) {
                warnings.add(bad + (bad == 1 ? " seam doesn't" : " seams don't") + " match; run tilecheck to see "
                        + (bad == 1 ? "it" : "them"));
            }
        } else {
            Sprite s = src.sprite();
            SheetPacker.Layout layout = SheetPacker.layout(s, SPRITE_PADDING, 0);
            for (Sprite sp : s.withVariants()) {
                String png = sp.name() + ".png", tres = sp.name() + ".tres", tscn = sp.name() + ".tscn";
                writePng(dir.resolve(png), SheetPacker.render(sp, layout));
                SpriteFramesWriter.Result frames = SpriteFramesWriter.write(sp, layout, join(resDir, png));
                writeGodotText(dir.resolve(tres), frames.text());
                names.add(sp.name());
                outputs.add(prefix + png);
                outputs.add(prefix + tres);
                if (config.scenes()) {
                    String first = frames.animations().isEmpty() ? null : frames.animations().getFirst();
                    writeGodotText(dir.resolve(tscn), SceneWriter.write(sp, first, join(resDir, tres)));
                    outputs.add(prefix + tscn);
                }
                if (sp == s) {
                    for (String f : frames.skipped()) {
                        warnings.add("frame '" + f + "' is in no animation, and an animation already has that name,"
                                + " so it isn't exported");
                    }
                }
            }
        }
        return new Result(names, outputs, warnings);
    }

    /** Writes the PNG only when its bytes change, so Godot doesn't re-import an identical image. */
    private static void writePng(Path file, java.awt.image.BufferedImage img) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(img, "png", bytes);
        byte[] data = bytes.toByteArray();
        if (Files.isRegularFile(file) && java.util.Arrays.equals(Files.readAllBytes(file), data)) return;
        Files.write(file, data);
    }

    private static void writeGodotText(Path file, String text) throws IOException {
        String old = Files.isRegularFile(file) ? Files.readString(file, StandardCharsets.UTF_8) : null;
        String updated = Uids.keep(old, text);
        if (!updated.equals(old)) Files.writeString(file, updated, StandardCharsets.UTF_8);
    }

    /** "res://art" + "x.png" is "res://art/x.png"; "res://" + "x.png" is "res://x.png". */
    static String join(String resDir, String child) {
        return resDir.endsWith("/") ? resDir + child : resDir + "/" + child;
    }

    static String sha256(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
