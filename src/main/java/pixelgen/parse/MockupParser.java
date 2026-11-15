package pixelgen.parse;

import pixelgen.model.Tileset;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses {@code .mockup} files: a small map of a tileset's tiles, to see how they fit together.
 *
 * <pre>
 * # comment
 * g = grass grass_b grass_c    a legend line: one character for one tile, or for several,
 * 1 = dirt_corner_nw           in which case each cell picks one of them (the same every time)
 * n = dirt_edge_n
 * map                          everything below is the map, one character per tile
 *   gggg
 *   g1nn                       '.' leaves a cell empty
 * </pre>
 */
public final class MockupParser {

    public static final int MAX_SIDE = 64;

    private MockupParser() {}

    /**
     * A parsed mock map.
     *
     * @param legend each map character's tiles (one, or several to pick from)
     * @param rows   the map, all rows equally long
     */
    public record Mockup(Map<Character, List<String>> legend, List<String> rows) {
        public int width() { return rows.getFirst().length(); }

        public int height() { return rows.size(); }

        /** The tile at a cell, or null for an empty one. Picks among several tiles the same way every time. */
        public String tile(int x, int y) {
            char c = rows.get(y).charAt(x);
            List<String> tiles = legend.get(c);
            if (tiles == null) return null;
            if (tiles.size() == 1) return tiles.getFirst();
            int h = x * 73856093 ^ y * 19349663;
            h ^= h >>> 13;
            h *= 0x5bd1e995;
            h ^= h >>> 15;
            return tiles.get(Math.floorMod(h, tiles.size()));
        }
    }

    public static Mockup parseFile(Path file, String shown, Tileset tileset) throws IOException, ParseException {
        return parse(Files.readString(file, StandardCharsets.UTF_8), shown, tileset);
    }

    public static Mockup parse(String text, String source, Tileset tileset) throws ParseException {
        List<String> errors = new ArrayList<>();
        Map<Character, List<String>> legend = new LinkedHashMap<>();
        Map<Character, Integer> legendLines = new HashMap<>();
        List<String> rows = new ArrayList<>();
        List<Integer> rowLines = new ArrayList<>();
        boolean inMap = false;
        String[] lines = text.split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String where = source + ":" + (i + 1) + ": ";
            if (inMap) {
                if (line.contains(" ")) {
                    errors.add(where + "map rows can't contain spaces; use one character per tile");
                } else {
                    rows.add(line);
                    rowLines.add(i + 1);
                }
                continue;
            }
            if (line.equals("map")) {
                inMap = true;
                continue;
            }
            String[] parts = line.split("\\s+");
            if (parts.length < 3 || parts[0].length() != 1 || !parts[1].equals("=")) {
                errors.add(where + "expected '<character> = <tile> [<tile> ...]' or 'map'");
                continue;
            }
            char c = parts[0].charAt(0);
            if (c == '.' || c == '#') {
                errors.add(where + "'" + c + "' can't be a legend character ('.' is an empty cell)");
                continue;
            }
            Integer first = legendLines.putIfAbsent(c, i + 1);
            if (first != null) {
                errors.add(where + "'" + c + "' is already defined on line " + first);
                continue;
            }
            List<String> tiles = new ArrayList<>();
            for (int j = 2; j < parts.length; j++) {
                if (tileset.sprite().frames().containsKey(parts[j])) {
                    tiles.add(parts[j]);
                } else {
                    errors.add(where + "no tile '" + parts[j] + "' in tileset '" + tileset.name() + "' (tiles: "
                            + String.join(", ", tileset.tiles()) + ")");
                }
            }
            if (tiles.size() == parts.length - 2) legend.put(c, List.copyOf(tiles));
        }
        if (!inMap) {
            errors.add(source + ": missing 'map' followed by the map rows");
        } else if (rows.isEmpty()) {
            errors.add(source + ": the map has no rows");
        } else {
            int width = rows.getFirst().length();
            for (int y = 0; y < rows.size(); y++) {
                String row = rows.get(y);
                String where = source + ":" + rowLines.get(y) + ": ";
                if (row.length() != width) {
                    errors.add(where + "map row " + y + " is " + row.length() + " wide; the first row is " + width);
                }
                for (int x = 0; x < row.length(); x++) {
                    char c = row.charAt(x);
                    if (c != '.' && !legend.containsKey(c) && !legendLines.containsKey(c)) {
                        errors.add(where + "map cell (" + x + "," + y + "): '" + c + "' isn't in the legend");
                    }
                }
            }
            if (width > MAX_SIDE || rows.size() > MAX_SIDE) {
                errors.add(source + ": the map is " + width + "x" + rows.size() + " tiles; at most " + MAX_SIDE
                        + " per side");
            }
        }
        if (!errors.isEmpty()) throw new ParseException(SpriteParser.sortByLine(errors, source));
        return new Mockup(Map.copyOf(legend), List.copyOf(rows));
    }
}
