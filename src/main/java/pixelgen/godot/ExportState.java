package pixelgen.godot;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * What the last export wrote: for each source, the hash it had and the files it produced. Kept in
 * {@code build/pixelgen/export.state}; {@code gradlew clean} deletes it, which makes the next
 * export write everything again.
 *
 * <pre>
 * project  C:/games/farm-game
 * source   assets/characters/farmer.sprite  &lt;sha-256&gt;
 * output   art/characters/farmer.png
 * </pre>
 *
 * @param project the Godot project the outputs are in (they are relative to it)
 * @param sources by source path, sorted so the file is stable
 */
public record ExportState(Path project, Map<String, Entry> sources) {

    public static final String FILE = "build/pixelgen/export.state";

    /** One source's hash and outputs (relative to the Godot project, with forward slashes). */
    public record Entry(String hash, List<String> outputs) {
        public Entry {
            outputs = List.copyOf(outputs);
        }
    }

    public ExportState {
        sources = new TreeMap<>(sources);
    }

    public static ExportState empty(Path project) { return new ExportState(project, Map.of()); }

    /** The saved state, or null when there is none or it can't be read (then everything is exported). */
    public static ExportState load(Path file) throws IOException {
        if (!Files.isRegularFile(file)) return null;
        Path project = null;
        Map<String, Entry> sources = new TreeMap<>();
        String source = null, hash = null;
        List<String> outputs = new ArrayList<>();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String[] f = line.split("\t");
            switch (f[0]) {
                case "project" -> project = f.length == 2 ? Path.of(f[1]) : null;
                case "source" -> {
                    if (source != null) sources.put(source, new Entry(hash, outputs));
                    if (f.length != 3) return null;
                    source = f[1];
                    hash = f[2];
                    outputs = new ArrayList<>();
                }
                case "output" -> {
                    if (source == null || f.length != 2) return null;
                    outputs.add(f[1]);
                }
                default -> {
                    if (!line.isBlank() && !line.startsWith("#")) return null;
                }
            }
        }
        if (source != null) sources.put(source, new Entry(hash, outputs));
        return project == null ? null : new ExportState(project, sources);
    }

    public String text() {
        StringBuilder t = new StringBuilder("# pixel-gen export state: the files each source produced\n");
        t.append("project\t").append(project.toString().replace('\\', '/')).append('\n');
        sources.forEach((source, e) -> {
            t.append("source\t").append(source).append('\t').append(e.hash()).append('\n');
            for (String o : e.outputs()) t.append("output\t").append(o).append('\n');
        });
        return t.toString();
    }

    /** Writes the state when it changed, so its timestamp means something to Gradle. */
    public void save(Path file) throws IOException {
        String text = text();
        if (Files.isRegularFile(file) && Files.readString(file, StandardCharsets.UTF_8).equals(text)) return;
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }
}
