package pixelgen.parse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ProjectFilesTest {

    @TempDir Path project;

    @Test
    void namesAreLookedUpInTheirFolderAndPathsAreKept() {
        assertEquals(project.resolve("palettes/farm.pal"), ProjectFiles.resolve("farm", project, "palettes", ".pal"));
        assertEquals(project.resolve("farm.pal"), ProjectFiles.resolve("farm.pal", project, "palettes", ".pal"));
        assertEquals(project.resolve("x/farm"), ProjectFiles.resolve("x/farm", project, "palettes", ".pal"));
        assertEquals(project.resolve("x\\farm"), ProjectFiles.resolve("x\\farm", project, "palettes", ".pal"));
    }

    @Test
    void displayIsProjectRelativeWithForwardSlashes() {
        assertEquals("assets/trees/oak.sprite", ProjectFiles.display(project, project.resolve("assets/trees/oak.sprite")));
        assertEquals("assets/oak.sprite", ProjectFiles.display(project, project.resolve("other/../assets/oak.sprite")));
        Path outside = project.getParent().resolve("elsewhere.sprite").toAbsolutePath();
        assertEquals(outside.toString().replace('\\', '/'), ProjectFiles.display(project, outside));
    }

    @Test
    void baseNameDropsOnlyTheGivenExtension() {
        assertEquals("apple", ProjectFiles.baseName(Path.of("assets/apple.sprite"), ".sprite"));
        assertEquals("apple.png", ProjectFiles.baseName(Path.of("apple.png"), ".sprite"));
    }
}
