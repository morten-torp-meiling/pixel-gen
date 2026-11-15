package pixelgen.render;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Node;
import pixelgen.model.Anim;
import pixelgen.model.Sprite;
import pixelgen.parse.Palettes;
import pixelgen.parse.SpriteParser;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GifWriterTest {

    @TempDir Path dir;

    // fx palette: K black, w white, ~ 33% black (alpha 0x55). Two 2x1 frames.
    private static final String TEXT = "size 2 1\npalette fx\nframe a\nK.\nend\nframe b\n~w\nend\n"
            + "anim go fps 4 loop: a b*2\nanim stop fps 10 once: b\n";

    private static Sprite sprite() throws Exception {
        return SpriteParser.parse(TEXT, "t.sprite", "t", n -> Palettes.load("fx", Path.of("src/test/resources")));
    }

    /** What a GIF file holds: images, delays and whether it loops. */
    private record Read(List<BufferedImage> images, List<Integer> delays, boolean loops) {}

    private static Read read(Path file) throws Exception {
        ImageReader reader = ImageIO.getImageReadersByFormatName("gif").next();
        try (ImageInputStream in = ImageIO.createImageInputStream(file.toFile())) {
            reader.setInput(in);
            int n = reader.getNumImages(true);
            List<BufferedImage> images = new ArrayList<>();
            List<Integer> delays = new ArrayList<>();
            boolean loops = false;
            for (int i = 0; i < n; i++) {
                images.add(reader.read(i));
                IIOMetadataNode root = (IIOMetadataNode) reader.getImageMetadata(i)
                        .getAsTree("javax_imageio_gif_image_1.0");
                for (Node c = root.getFirstChild(); c != null; c = c.getNextSibling()) {
                    if (c.getNodeName().equals("GraphicControlExtension")) {
                        delays.add(Integer.parseInt(((IIOMetadataNode) c).getAttribute("delayTime")));
                    }
                    if (c.getNodeName().equals("ApplicationExtensions")) {
                        IIOMetadataNode app = (IIOMetadataNode) c.getFirstChild();
                        loops |= app.getAttribute("applicationID").equals("NETSCAPE");
                    }
                }
            }
            return new Read(images, delays, loops);
        } finally {
            reader.dispose();
        }
    }

    @Test
    void delaysAreHundredthsOfASecondAndKeepTheTotalTrue() {
        assertEquals(List.of(25, 50), GifWriter.delays(new Anim("x", 4, true,
                List.of(new Anim.Step("a", 1), new Anim.Step("b", 2)))));
        // 3 fps: 33.3 each, rounded so the three add up to exactly 100
        List<Integer> d = GifWriter.delays(new Anim("x", 3, true,
                List.of(new Anim.Step("a", 1), new Anim.Step("a", 1), new Anim.Step("a", 1))));
        assertEquals(100, d.stream().mapToInt(Integer::intValue).sum(), d.toString());
        assertEquals(List.of(1), GifWriter.delays(new Anim("x", 120, true, List.of(new Anim.Step("a", 1)))),
                "never 0, which browsers treat as 'as slow as they like'");
    }

    @Test
    void loopingGifReadsBackWithItsFramesTimingAndLoop() throws Exception {
        Sprite s = sprite();
        Path file = dir.resolve("go.gif");
        GifWriter.write(GifWriter.animation(s, s.anim("go"), 3, null), file);
        Read r = read(file);
        assertEquals(2, r.images().size());
        assertEquals(List.of(25, 50), r.delays());
        assertTrue(r.loops());
        BufferedImage a = r.images().getFirst();
        assertEquals(6, a.getWidth());
        assertEquals(3, a.getHeight());
        assertEquals(0xFF000000, a.getRGB(1, 1));
        assertEquals(0, a.getRGB(4, 1) >>> 24, "transparent pixel stays transparent");
        BufferedImage b = r.images().get(1);
        assertEquals(0, b.getRGB(1, 1) >>> 24, "without a background, the 33% shadow is dropped");
        assertEquals(0xFFFFFFFF, b.getRGB(4, 1));
    }

    @Test
    void onceGifHasNoLoopExtension() throws Exception {
        Sprite s = sprite();
        Path file = dir.resolve("stop.gif");
        GifWriter.write(GifWriter.animation(s, s.anim("stop"), 1, null), file);
        Read r = read(file);
        assertEquals(1, r.images().size());
        assertEquals(List.of(10), r.delays());
        assertFalse(r.loops());
    }

    @Test
    void backgroundShowsThroughTranslucentAndEmptyPixels() throws Exception {
        Sprite s = sprite();
        Path file = dir.resolve("bg.gif");
        GifWriter.write(GifWriter.animation(s, s.anim("go"), 1, new Color(0xFFFFFF)), file);
        BufferedImage b = read(file).images().get(1);
        // 33% black over white: 255 * (1 - 0x55/255) = 170
        assertEquals(0xFFAAAAAA, b.getRGB(0, 0));
        assertEquals(0xFFFFFFFF, read(file).images().getFirst().getRGB(1, 0), "empty pixel shows the background");
    }

    @Test
    void tooManyColoursIsAnError() {
        BufferedImage img = new BufferedImage(300, 1, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < 300; x++) img.setRGB(x, 0, 0xFF000000 | x * 1000);
        GifWriter.Animation anim = new GifWriter.Animation(List.of(img), List.of(10), true);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> GifWriter.write(anim, dir.resolve("x.gif")));
        assertTrue(e.getMessage().contains("300 colours"), e.getMessage());
    }
}
