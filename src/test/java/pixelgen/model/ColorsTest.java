package pixelgen.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ColorsTest {

    @Test
    void parsesHexWithAndWithoutHash() {
        assertEquals(0x1c1520, Colors.parseHex("#1c1520"));
        assertEquals(0xABCDEF, Colors.parseHex("abcdef"));
    }

    @Test
    void rejectsMalformedHex() {
        for (String bad : new String[] {"#12345", "#1234567", "#12345678", "#gg0000", "", "#"}) {
            assertThrows(IllegalArgumentException.class, () -> Colors.parseHex(bad), bad);
        }
    }

    @Test
    void formatsHexInLowerCaseWithHash() {
        assertEquals("#00a0ff", Colors.toHex(0x00A0FF));
    }

    @Test
    void parsesHexWithOptionalAlpha() {
        assertEquals(0xFF1C1520, Colors.parseHexWithAlpha("#1c1520"));
        assertEquals(0x551C1520, Colors.parseHexWithAlpha("#1c152055"));
        for (String bad : new String[] {"#1234567", "#123456789", "#1c1520zz", ""}) {
            assertThrows(IllegalArgumentException.class, () -> Colors.parseHexWithAlpha(bad), bad);
        }
    }

    @Test
    void formatsAlphaOnlyWhenTranslucent() {
        assertEquals("#1c1520", Colors.toHex(0x1C1520, 255));
        assertEquals("#1c152055", Colors.toHex(0x1C1520, 0x55));
    }

    @Test
    void lightnessRunsFromBlackToWhite() {
        assertEquals(0.0, Colors.lightness(0x000000), 1e-6);
        assertEquals(1.0, Colors.lightness(0xFFFFFF), 1e-3);
        assertTrue(Colors.lightness(0x808080) > 0.5 && Colors.lightness(0x808080) < 0.7);
    }

    @Test
    void yellowLooksLighterThanBlueAtFullStrength() {
        assertTrue(Colors.lightness(0xFFFF00) > Colors.lightness(0x0000FF));
    }

    @Test
    void distanceIsZeroForSameColourAndSymmetric() {
        assertEquals(0.0, Colors.distance(0x123456, 0x123456), 1e-9);
        assertEquals(Colors.distance(0xff0000, 0x00ff00), Colors.distance(0x00ff00, 0xff0000), 1e-12);
        assertTrue(Colors.distance(0xff0000, 0xfe0000) < Colors.distance(0xff0000, 0x00ff00));
    }
}
