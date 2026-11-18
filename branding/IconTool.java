import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

/** Renders PNG icon sizes and a Windows .ico from the 512px logo. */
public final class IconTool {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]);
        BufferedImage source = ImageIO.read(root.resolve("logo-512.png").toFile());
        if (source == null) throw new IllegalStateException("logo-512.png not readable");

        Path pngDir = root.resolve("png");
        Files.createDirectories(pngDir);
        int[] pngSizes = {512, 256, 128, 64, 48, 32, 16};
        for (int size : pngSizes) {
            BufferedImage scaled = resize(source, size);
            ImageIO.write(scaled, "png", pngDir.resolve("icon-" + size + ".png").toFile());
        }

        int[] icoSizes = {256, 128, 64, 48, 32, 24, 16};
        Path ico = root.resolve("startcoach.ico");
        writeIco(ico, source, icoSizes);
        System.out.println("icons written: " + pngDir.toAbsolutePath() + " and " + ico.toAbsolutePath());
    }

    private static BufferedImage resize(BufferedImage source, int size) {
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(source, 0, 0, size, size, null);
        g.dispose();
        return out;
    }

    /** jpackage rewrites .ico files and only understands classic 32-bit DIB entries. */
    private static byte[] dibIcon(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int maskRowBytes = ((width + 31) / 32) * 4;
        int xorBytes = width * height * 4;
        int andBytes = maskRowBytes * height;
        ByteBuffer out = ByteBuffer.allocate(40 + xorBytes + andBytes).order(ByteOrder.LITTLE_ENDIAN);
        out.putInt(40);
        out.putInt(width);
        out.putInt(height * 2);
        out.putShort((short) 1);
        out.putShort((short) 32);
        out.putInt(0);
        out.putInt(xorBytes);
        out.putInt(0);
        out.putInt(0);
        out.putInt(0);
        out.putInt(0);
        for (int y = height - 1; y >= 0; y--) {
            for (int x = 0; x < width; x++) {
                int argb = image.getRGB(x, y);
                out.put((byte) (argb & 0xFF));
                out.put((byte) ((argb >> 8) & 0xFF));
                out.put((byte) ((argb >> 16) & 0xFF));
                out.put((byte) ((argb >>> 24) & 0xFF));
            }
        }
        // AND mask rows stay zero; the alpha channel carries transparency.
        return out.array();
    }

    private static void writeIco(Path target, BufferedImage source, int[] sizes) throws IOException {
        byte[][] images = new byte[sizes.length][];
        for (int i = 0; i < sizes.length; i++) {
            images[i] = dibIcon(resize(source, sizes[i]));
        }
        ByteBuffer header = ByteBuffer.allocate(6 + 16 * sizes.length).order(ByteOrder.LITTLE_ENDIAN);
        header.putShort((short) 0);
        header.putShort((short) 1);
        header.putShort((short) sizes.length);
        int offset = 6 + 16 * sizes.length;
        for (int i = 0; i < sizes.length; i++) {
            int size = sizes[i];
            header.put((byte) (size >= 256 ? 0 : size));
            header.put((byte) (size >= 256 ? 0 : size));
            header.put((byte) 0);
            header.put((byte) 0);
            header.putShort((short) 1);
            header.putShort((short) 32);
            header.putInt(images[i].length);
            header.putInt(offset);
            offset += images[i].length;
        }
        try (FileOutputStream out = new FileOutputStream(target.toFile())) {
            out.write(header.array());
            for (byte[] image : images) out.write(image);
        }
    }
}
