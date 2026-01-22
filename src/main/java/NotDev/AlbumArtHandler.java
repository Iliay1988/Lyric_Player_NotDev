package NotDev;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;

public class AlbumArtHandler {

    public static ImageIcon createIcon(byte[] artBytes, int size) {
        if (artBytes == null || artBytes.length == 0) return null;
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(artBytes));
            if (img == null) return null;

            // Просто масштабируем оригинал
            Image scaled = img.getScaledInstance(size, size, Image.SCALE_SMOOTH);

            img.flush(); // Очистка памяти
            return new ImageIcon(scaled);
        } catch (Exception e) {
            return null;
        }
    }

    public static BufferedImage createBlurredBackground(byte[] artBytes, int width, int height) {
        if (artBytes == null || artBytes.length == 0) return null;
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(artBytes));
            if (img == null) return null;

            BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = resized.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(img, 0, 0, width, height, null);
            g.dispose();

            int radius = 20;
            int size = radius * radius;
            float[] matrix = new float[size];
            for (int i = 0; i < size; i++) matrix[i] = 1.0f / size;
            ConvolveOp op = new ConvolveOp(new Kernel(radius, radius, matrix), ConvolveOp.EDGE_NO_OP, null);
            BufferedImage blurred = op.filter(resized, null);

            Graphics2D g2 = blurred.createGraphics();
            g2.setColor(new Color(0, 0, 0, 160));
            g2.fillRect(0, 0, width, height);
            g2.dispose();

            img.flush();
            resized.flush();

            return blurred;
        } catch (Exception e) {
            return null;
        }
    }
}