package pixelgen.godot;

import org.junit.jupiter.api.Test;
import pixelgen.model.Sprite;
import pixelgen.parse.Palettes;
import pixelgen.parse.SpriteParser;
import pixelgen.render.SheetPacker;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SpriteFramesWriterTest {

    private static Sprite sprite(String text) throws Exception {
        return SpriteParser.parse(text, "t.sprite", "t", n -> Palettes.load("basic", Path.of("src/test/resources")));
    }

    /** Git may check text files out with CRLF on Windows; the format itself uses LF. */
    private static String lf(String s) { return s.replace("\r\n", "\n"); }

    @Test
    void matchesTheHandWrittenGoldenFile() throws Exception {
        Sprite s = sprite(Files.readString(Path.of("src/test/resources/sprites/blink.sprite")));
        SpriteFramesWriter.Result r = SpriteFramesWriter.write(s, SheetPacker.layout(s, 1, 0), "res://sprites/blink.png");
        assertEquals(lf(Files.readString(Path.of("src/test/resources/golden/blink.tres"))), r.text());
        assertEquals(List.of("blink", "fade", "lit"), r.animations());
        assertEquals(List.of(), r.skipped());
    }

    @Test
    void stillFrameNamedLikeAnAnimationIsLeftOut() throws Exception {
        Sprite s = sprite("size 1 1\npalette basic\nframe a\nK\nend\nframe idle\nw\nend\nanim idle fps 2 loop: a\n");
        SpriteFramesWriter.Result r = SpriteFramesWriter.write(s, SheetPacker.layout(s, 1, 0), "res://x.png");
        assertEquals(List.of("idle"), r.skipped());
        assertFalse(r.text().contains("AtlasTexture_idle"), r.text());
        assertTrue(r.text().contains("load_steps=3 "), r.text());
    }

    @Test
    void godotNumberAndStringStyle() {
        assertEquals("8.0", SpriteFramesWriter.number(8));
        assertEquals("0.5", SpriteFramesWriter.number(0.5));
        assertEquals("12.25", SpriteFramesWriter.number(12.25));
        assertEquals("\"a\\\"b\\\\c\"", SpriteFramesWriter.quote("a\"b\\c"));
    }
}
