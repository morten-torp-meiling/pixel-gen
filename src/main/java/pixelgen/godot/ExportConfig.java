package pixelgen.godot;

import pixelgen.parse.ParseException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Export settings from {@code pixelgen.conf} in the project folder.
 *
 * <pre>
 * # Folder holding project.godot; relative paths start at this file's folder.
 * godot.project = ../farm-game
 * # Folder in the Godot project that receives the art; the folders under assets/ are kept below it.
 * godot.art = res://art
 * # Also write a ready-made scene (an AnimatedSprite2D) per sprite.
 * scenes = true
 * </pre>
 *
 * @param project the Godot project folder (absolute)
 * @param art     the {@code res://} folder for exported files, without a trailing slash
 * @param scenes  write a {@code .tscn} per sprite
 */
public record ExportConfig(Path project, String art, boolean scenes) {

    public static final String FILE = "pixelgen.conf";
    public static final String DEFAULT_ART = "res://art";

    /** The folder on disk behind a {@code res://} path. */
    public Path toDisk(String resPath) {
        String rel = resPath.substring("res://".length());
        return rel.isEmpty() ? project : project.resolve(rel);
    }

    public static ExportConfig load(Path projectDir) throws IOException, ParseException {
        Path file = projectDir.resolve(FILE);
        if (!Files.isRegularFile(file)) {
            throw new NoSuchFileException(file.toString(), null, "no " + FILE + " in " + projectDir
                    + "; create one with a line like 'godot.project = ../my-game' (the folder with project.godot)");
        }
        return parse(Files.readString(file, StandardCharsets.UTF_8), FILE, projectDir);
    }

    /** Parses settings; relative paths resolve against {@code baseDir}. */
    public static ExportConfig parse(String text, String source, Path baseDir) throws ParseException {
        List<String> errors = new ArrayList<>();
        Map<String, Integer> seen = new HashMap<>();
        String project = null, art = DEFAULT_ART;
        boolean scenes = true;
        int projectLine = 0;
        String[] lines = text.split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String where = source + ":" + (i + 1) + ": ";
            int eq = line.indexOf('=');
            if (eq < 0) {
                errors.add(where + "expected 'key = value'");
                continue;
            }
            String key = line.substring(0, eq).strip(), value = line.substring(eq + 1).strip();
            Integer first = seen.putIfAbsent(key, i + 1);
            if (first != null) {
                errors.add(where + "'" + key + "' is already set on line " + first);
                continue;
            }
            switch (key) {
                case "godot.project" -> {
                    project = value;
                    projectLine = i + 1;
                }
                case "godot.art" -> {
                    if (!value.startsWith("res://") || value.contains("\\") || value.contains("..")) {
                        errors.add(where + "godot.art must be a folder like res://art, not '" + value + "'");
                    } else {
                        art = value.endsWith("/") && !value.equals("res://") ? value.substring(0, value.length() - 1)
                                : value;
                    }
                }
                case "scenes" -> {
                    if (value.equals("true") || value.equals("false")) scenes = value.equals("true");
                    else errors.add(where + "scenes must be true or false, not '" + value + "'");
                }
                default -> errors.add(where + "unknown setting '" + key + "'; known: godot.project, godot.art, scenes");
            }
        }
        Path dir = null;
        if (project == null) {
            errors.add(source + ": missing 'godot.project = <folder with project.godot>'");
        } else {
            dir = baseDir.resolve(project).toAbsolutePath().normalize();
            if (!Files.isRegularFile(dir.resolve("project.godot"))) {
                errors.add(source + ":" + projectLine + ": no project.godot in " + dir);
            }
        }
        if (!errors.isEmpty()) throw new ParseException(errors);
        return new ExportConfig(dir, art, scenes);
    }
}
