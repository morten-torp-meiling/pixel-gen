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
    void blankIsAllTransparent() {
        assertEquals(Grid.of("...", "..."), Grid.blank(3, 2));
    }

    @Test
    void stampPaintsOnlyVisibleCellsAtTheOffset() {
        Grid base = Grid.of("wwww", "wwww", "wwww");
        Grid part = Grid.of("K.", ".k");
        assertEquals(Grid.of("wwww", "wKww", "wwkw"), base.stamp(part, 1, 1));
        assertEquals(Grid.of("wwww", "wwww", "wwww"), base, "the original is unchanged");
    }

    @Test
    void laterStampsCoverEarlierOnes() {
        Grid g = Grid.blank(3, 1).stamp(Grid.of("KKK"), 0, 0).stamp(Grid.of("w"), 1, 0);
        assertEquals(Grid.of("KwK"), g);
    }

    @Test
    void stampsAreCutOffAtTheEdges() {
        Grid g = Grid.blank(3, 2);
        assertEquals(Grid.of("..K", "..."), g.stamp(Grid.of("KK"), 2, 0));
        assertEquals(Grid.of("k..", "..."), g.stamp(Grid.of("K.", ".k"), -1, -1));
        assertTrue(g.clips(Grid.of("KK"), 2, 0));
        assertFalse(g.clips(Grid.of("KK"), 1, 0));
        assertFalse(g.clips(Grid.of("K."), 2, 0), "only visible cells count");
    }

    @Test
    void flipsMirrorTheGrid() {
        Grid g = Grid.of("ab", "cd", "ef");
        assertEquals(Grid.of("ba", "dc", "fe"), g.flipX());
        assertEquals(Grid.of("ef", "cd", "ab"), g.flipY());
        assertEquals(g, g.flipX().flipX());
    }

    @Test
    void recolorSwapsAllAtOnce() {
        Grid g = Grid.of("abc.");
        assertEquals(Grid.of("bac."), g.recolor(java.util.Map.of('a', 'b', 'b', 'a')));
        assertEquals(Grid.of("a.c."), g.recolor(java.util.Map.of('b', '.')));
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
