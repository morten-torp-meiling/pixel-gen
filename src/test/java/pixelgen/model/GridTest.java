package pixelgen.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GridTest {

    @Test
    void readsCellsByXAndY() {
        Grid g = Grid.of("ab.", "cde");
        assertEquals(3, g.width());
        assertEquals(2, g.height());
        assertEquals('a', g.at(0, 0));
        assertEquals('.', g.at(2, 0));
        assertEquals('e', g.at(2, 1));
    }

    @Test
    void rejectsRaggedOrEmptyRows() {
        assertThrows(IllegalArgumentException.class, () -> Grid.of("abc", "ab"));
        assertThrows(IllegalArgumentException.class, () -> new Grid(List.of()));
        assertThrows(IllegalArgumentException.class, () -> Grid.of(""));
    }

    @Test
    void outOfBoundsAccessFails() {
        Grid g = Grid.of("ab");
        assertThrows(IndexOutOfBoundsException.class, () -> g.at(2, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> g.at(0, -1));
    }

    @Test
    void blankMeansAllTransparent() {
        assertTrue(Grid.of("..", "..").isBlank());
        assertFalse(Grid.of("..", ".K").isBlank());
    }

    @Test
    void equalityAndRowsRoundTrip() {
        Grid g = Grid.of("ab", "cd");
        assertEquals(g, new Grid(g.rows()));
        assertEquals(g.hashCode(), Grid.of("ab", "cd").hashCode());
        assertNotEquals(g, Grid.of("abcd"));
        assertEquals("ab\ncd", g.toString());
    }
}
