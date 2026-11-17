package pixelgen.parse;

import pixelgen.model.Palette;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

/** Finds palettes by name ({@code farm} → {@code palettes/farm.pal}) or by file path. */
public final class Palettes {

    public static final String FOLDER = "palettes";

    private Palettes() {}

    /** Resolves a name or path against {@code projectDir}. */
    public static Path resolve(String nameOrPath, Path projectDir) {
        return ProjectFiles.resolve(nameOrPath, projectDir, FOLDER, ".pal");
    }

    public static Palette load(String nameOrPath, Path projectDir) throws IOException, ParseException {
        Path file = resolve(nameOrPath, projectDir);
        if (!Files.isRegularFile(file)) {
            throw new NoSuchFileException(file.toString(), null, "palette not found");
        }
        return PaletteParser.parseFile(file);
    }
}
