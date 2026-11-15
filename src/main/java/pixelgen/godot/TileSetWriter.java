package pixelgen.godot;

import pixelgen.model.Tileset;
import pixelgen.render.SheetPacker;

import java.awt.Rectangle;

import static pixelgen.godot.SpriteFramesWriter.quote;

/**
 * Writes a Godot 4 {@code TileSet} resource ({@code .tres}, text format 3) with one
 * {@code TileSetAtlasSource} over the atlas image.
 * <ul>
 *   <li>Each tile is declared at its atlas coordinates (column, row), so it can be painted in a
 *       TileMapLayer straight away.</li>
 *   <li>A custom data layer called {@code name} holds each tile's name, so game code can find
 *       tiles by name: {@code tile_data.get_custom_data("name")}.</li>
 *   <li>Atlas padding becomes the source's {@code margins} and {@code separation}. Padding is
 *       rarely needed: Godot pads each tile internally ({@code use_texture_padding}, on by
 *       default), which stops lines appearing between tiles.</li>
 * </ul>
 * The output is deterministic; Godot adds a uid the first time the editor saves the file.
 */
public final class TileSetWriter {

    /** Godot's Variant.Type for String, used as the custom data layer's type. */
    static final int TYPE_STRING = 4;

    private TileSetWriter() {}

    public static String write(Tileset tileset, SheetPacker.Layout layout, String texturePath) {
        int w = tileset.tileWidth(), h = tileset.tileHeight(), pad = layout.padding();
        StringBuilder t = new StringBuilder();
        t.append("[gd_resource type=\"TileSet\" load_steps=3 format=3]\n\n");
        t.append("[ext_resource type=\"Texture2D\" path=").append(quote(texturePath)).append(" id=\"1_atlas\"]\n\n");
        t.append("[sub_resource type=\"TileSetAtlasSource\" id=\"TileSetAtlasSource_atlas\"]\n");
        t.append("texture = ExtResource(\"1_atlas\")\n");
        if (pad > 0) {
            t.append("margins = ").append(vector(pad, pad)).append('\n');
            t.append("separation = ").append(vector(pad, pad)).append('\n');
        }
        t.append("texture_region_size = ").append(vector(w, h)).append('\n');
        layout.frames().forEach((name, r) -> {
            String coords = column(r, w, pad) + ":" + row(r, h, pad);
            t.append(coords).append("/0 = 0\n");
            t.append(coords).append("/0/custom_data_0 = ").append(quote(name)).append('\n');
        });
        t.append('\n');
        t.append("[resource]\n");
        t.append("tile_size = ").append(vector(w, h)).append('\n');
        t.append("custom_data_layer_0/name = \"name\"\n");
        t.append("custom_data_layer_0/type = ").append(TYPE_STRING).append('\n');
        t.append("sources/0 = SubResource(\"TileSetAtlasSource_atlas\")\n");
        return t.toString();
    }

    /** A tile's atlas column: Godot finds it at margin + column * (size + separation). */
    static int column(Rectangle r, int w, int pad) { return (r.x - pad) / (w + pad); }

    static int row(Rectangle r, int h, int pad) { return (r.y - pad) / (h + pad); }

    private static String vector(int x, int y) { return "Vector2i(" + x + ", " + y + ")"; }
}
