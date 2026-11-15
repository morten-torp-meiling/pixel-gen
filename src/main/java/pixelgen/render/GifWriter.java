package pixelgen.render;

import pixelgen.model.Anim;
import pixelgen.model.Sprite;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes animated GIFs with the JDK's own GIF encoder.
 * <p>
 * GIF has no partial transparency: a pixel is either a colour or fully see-through. So frames are
 * either drawn over a solid background (translucent colours such as a shadow blend into it), or,
 * with no background, pixels at least half opaque become solid and the rest transparent.
 */
public final class GifWriter {

    /** GIF stores at most 256 colours per table. */
    public static final int MAX_COLOURS = 256;

    private GifWriter() {}

    /** Frame images and their delays in hundredths of a second, ready to write. */
    public record Animation(List<BufferedImage> frames, List<Integer> delays, boolean loop) {
        public Animation {
            if (frames.isEmpty() || frames.size() != delays.size()) {
                throw new IllegalArgumentException("need one delay per frame, and at least one frame");
            }
            frames = List.copyOf(frames);
            delays = List.copyOf(delays);
        }
    }

    /**
     * Renders an animation of a sprite.
     *
     * @param background solid colour behind the sprite, or null for a transparent GIF
     */
    public static Animation animation(Sprite sprite, Anim anim, int scale, Color background) {
        List<BufferedImage> frames = new ArrayList<>();
        for (Anim.Step step : anim.steps()) {
            BufferedImage img = Renderer.render(sprite.frame(step.frame()).grid(), sprite.palette(), scale);
            frames.add(flatten(img, background));
        }
        return new Animation(frames, delays(anim), anim.loop());
    }

    /**
     * Delays in hundredths of a second (GIF's unit). Each delay is rounded so the total stays
     * true: at 3 fps, 33 + 34 + 33 rather than 33 + 33 + 33.
     */
    public static List<Integer> delays(Anim anim) {
        List<Integer> delays = new ArrayList<>();
        double t = 0;
        for (Anim.Step step : anim.steps()) {
            long start = Math.round(t * 100);
            t += step.duration() / anim.fps();
            delays.add((int) Math.max(1, Math.round(t * 100) - start));
        }
        return delays;
    }

    /** Over a background, or with alpha reduced to fully opaque or fully transparent. */
    static BufferedImage flatten(BufferedImage img, Color background) {
        int w = img.getWidth(), h = img.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        if (background != null) {
            Graphics2D g = out.createGraphics();
            try {
                g.setColor(background);
                g.fillRect(0, 0, w, h);
                g.drawImage(img, 0, 0, null);
            } finally {
                g.dispose();
            }
            return out;
        }
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = img.getRGB(x, y);
                out.setRGB(x, y, (p >>> 24) >= 128 ? 0xFF000000 | p : 0);
            }
        }
        return out;
    }

    /** Writes the animation; loops forever when {@code loop}, otherwise plays once. */
    public static void write(Animation anim, Path file) throws IOException {
        // One shared colour table; index 0 is transparent when any pixel is.
        Map<Integer, Integer> index = new LinkedHashMap<>();
        boolean transparent = false;
        for (BufferedImage f : anim.frames()) {
            for (int y = 0; y < f.getHeight(); y++) {
                for (int x = 0; x < f.getWidth(); x++) {
                    if ((f.getRGB(x, y) >>> 24) == 0) transparent = true;
                }
            }
        }
        if (transparent) index.put(0, 0);
        for (BufferedImage f : anim.frames()) {
            for (int y = 0; y < f.getHeight(); y++) {
                for (int x = 0; x < f.getWidth(); x++) {
                    int p = f.getRGB(x, y);
                    if ((p >>> 24) != 0) index.putIfAbsent(p & 0xFFFFFF | 0xFF000000, index.size());
                }
            }
        }
        if (index.size() > MAX_COLOURS) {
            throw new IllegalArgumentException("the animation uses " + index.size() + " colours; a GIF holds "
                    + MAX_COLOURS);
        }
        int n = Math.max(2, index.size());
        byte[] r = new byte[n], g = new byte[n], b = new byte[n];
        index.forEach((argb, i) -> {
            r[i] = (byte) (argb >> 16);
            g[i] = (byte) (argb >> 8);
            b[i] = (byte) (int) argb;
        });
        if (transparent) {
            // The transparent slot gets a colour no pixel uses; a duplicate (such as black) can be
            // merged with it by decoders, turning those pixels see-through.
            int spare = 0xFF00FF;
            while (index.containsKey(0xFF000000 | spare)) spare = (spare + 0x010203) & 0xFFFFFF;
            r[0] = (byte) (spare >> 16);
            g[0] = (byte) (spare >> 8);
            b[0] = (byte) spare;
        }
        IndexColorModel model = new IndexColorModel(8, n, r, g, b, transparent ? 0 : -1);

        ImageWriter writer = ImageIO.getImageWritersByFormatName("gif").next();
        Files.deleteIfExists(file);
        try (ImageOutputStream out = ImageIO.createImageOutputStream(file.toFile())) {
            writer.setOutput(out);
            writer.prepareWriteSequence(null);
            for (int i = 0; i < anim.frames().size(); i++) {
                BufferedImage src = anim.frames().get(i);
                BufferedImage indexed = new BufferedImage(src.getWidth(), src.getHeight(),
                        BufferedImage.TYPE_BYTE_INDEXED, model);
                WritableRaster raster = indexed.getRaster();
                for (int y = 0; y < src.getHeight(); y++) {
                    for (int x = 0; x < src.getWidth(); x++) {
                        int p = src.getRGB(x, y);
                        raster.setSample(x, y, 0, (p >>> 24) == 0 ? 0 : index.get(p | 0xFF000000));
                    }
                }
                IIOMetadata meta = writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(indexed), null);
                String format = meta.getNativeMetadataFormatName();
                IIOMetadataNode root = (IIOMetadataNode) meta.getAsTree(format);
                child(root, "ImageDescriptor").setAttribute("interlaceFlag", "FALSE");  // rows in order
                // An explicit colour table: without one the encoder may fall back to a default palette.
                IIOMetadataNode table = child(root, "LocalColorTable");
                while (table.getLength() > 0) table.removeChild(table.getFirstChild());
                int tableSize = 2;
                while (tableSize < n) tableSize *= 2;
                table.setAttribute("sizeOfLocalColorTable", String.valueOf(tableSize));
                table.setAttribute("sortFlag", "FALSE");
                for (int c = 0; c < tableSize; c++) {
                    IIOMetadataNode entry = new IIOMetadataNode("ColorTableEntry");
                    entry.setAttribute("index", String.valueOf(c));
                    entry.setAttribute("red", String.valueOf(c < n ? r[c] & 0xFF : 0));
                    entry.setAttribute("green", String.valueOf(c < n ? g[c] & 0xFF : 0));
                    entry.setAttribute("blue", String.valueOf(c < n ? b[c] & 0xFF : 0));
                    table.appendChild(entry);
                }
                IIOMetadataNode gce = child(root, "GraphicControlExtension");
                gce.setAttribute("disposalMethod", "restoreToBackgroundColor");
                gce.setAttribute("userInputFlag", "FALSE");
                gce.setAttribute("transparentColorFlag", transparent ? "TRUE" : "FALSE");
                gce.setAttribute("transparentColorIndex", "0");
                gce.setAttribute("delayTime", String.valueOf(anim.delays().get(i)));
                if (i == 0 && anim.loop()) {
                    IIOMetadataNode app = new IIOMetadataNode("ApplicationExtension");
                    app.setAttribute("applicationID", "NETSCAPE");
                    app.setAttribute("authenticationCode", "2.0");
                    app.setUserObject(new byte[] {1, 0, 0});  // loop count 0 = forever
                    child(root, "ApplicationExtensions").appendChild(app);
                }
                meta.setFromTree(format, root);
                writer.writeToSequence(new IIOImage(indexed, null, meta), null);
            }
            writer.endWriteSequence();
        } finally {
            writer.dispose();
        }
    }

    private static IIOMetadataNode child(IIOMetadataNode parent, String name) {
        for (int i = 0; i < parent.getLength(); i++) {
            if (parent.item(i).getNodeName().equals(name)) return (IIOMetadataNode) parent.item(i);
        }
        IIOMetadataNode node = new IIOMetadataNode(name);
        parent.appendChild(node);
        return node;
    }
}
