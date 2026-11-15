package pixelgen.godot;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Keeps the uid Godot gave a resource or scene when the export overwrites it.
 * <p>
 * The editor adds {@code uid="uid://..."} to a {@code .tres} or {@code .tscn} header when it saves
 * the file, and other scenes refer to the file by that uid. A rewrite without it would make Godot
 * assign a new one and warn in every scene using the old one.
 */
public final class Uids {

    private static final Pattern UID = Pattern.compile(" uid=\"(uid://[A-Za-z0-9]+)\"");

    private Uids() {}

    /** The uid in the first line of a Godot text file, or null. */
    public static String find(String godotText) {
        int end = godotText.indexOf('\n');
        Matcher m = UID.matcher(end < 0 ? godotText : godotText.substring(0, end));
        return m.find() ? m.group(1) : null;
    }

    /**
     * The new text with the old file's uid in its header (added just before the closing
     * bracket, where Godot writes it). Unchanged when the old text has no uid, or is null.
     */
    public static String keep(String oldText, String newText) {
        String uid = oldText == null ? null : find(oldText);
        if (uid == null || find(newText) != null) return newText;
        int end = newText.indexOf("]\n");
        if (!newText.startsWith("[gd_") || end < 0) return newText;
        return newText.substring(0, end) + " uid=\"" + uid + "\"" + newText.substring(end);
    }
}
