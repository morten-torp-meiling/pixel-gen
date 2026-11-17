package pixelgen.parse;

import java.nio.file.Path;

/** Finding project files by name and showing their paths in messages. */
public final class ProjectFiles {

    private ProjectFiles() {}

    /**
     * Resolves a name or path against {@code projectDir}: a bare name ({@code farm}) is looked up as
     * {@code folder/name + extension}; anything with the extension or a slash is a path.
     */
    public static Path resolve(String nameOrPath, Path projectDir, String folder, String extension) {
        if (nameOrPath.endsWith(extension) || nameOrPath.contains("/") || nameOrPath.contains("\\")) {
            return projectDir.resolve(nameOrPath);
        }
        return projectDir.resolve(folder).resolve(nameOrPath + extension);
    }

    /** The file's name without {@code extension} ({@code apple} for {@code apple.sprite}): its default name. */
    public static String baseName(Path file, String extension) {
        String name = file.getFileName().toString();
        return name.endsWith(extension) ? name.substring(0, name.length() - extension.length()) : name;
    }

    /** Project-relative path with forward slashes for a file inside the project, else the full path. */
    public static String display(Path projectDir, Path file) {
        Path abs = file.toAbsolutePath().normalize();
        Path base = projectDir.toAbsolutePath().normalize();
        return (abs.startsWith(base) ? base.relativize(abs) : abs).toString().replace('\\', '/');
    }
}
