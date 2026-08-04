package com.tontinemarche.util;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

/** Compression / redimensionnement d'images raster côté serveur (photos, pièces jointes). */
public final class ImageCompressionUtil {

    private static final Set<String> RASTER_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif"
    );

    private ImageCompressionUtil() {
    }

    public static boolean isRasterImage(String contentType) {
        return contentType != null && RASTER_TYPES.contains(contentType.toLowerCase(Locale.ROOT));
    }

    /**
     * Compresse l'image sur disque si elle dépasse les limites. Remplace le fichier par un JPEG optimisé.
     *
     * @return nouveau nom de fichier (avec extension .jpg) si conversion effectuée, sinon null
     */
    public static String compressFileIfNeeded(Path file, String contentType, int maxDimension, long maxBytes,
                                            float jpegQuality) throws IOException {
        if (!isRasterImage(contentType)) {
            return null;
        }
        long size = Files.size(file);
        BufferedImage source = ImageIO.read(file.toFile());
        if (source == null) {
            return null;
        }

        int width = source.getWidth();
        int height = source.getHeight();
        boolean needsResize = width > maxDimension || height > maxDimension;
        boolean needsCompress = size > maxBytes;

        if (!needsResize && !needsCompress) {
            return null;
        }

        BufferedImage scaled = scale(source, maxDimension);
        Path jpegPath = file.resolveSibling(stripExtension(file.getFileName().toString()) + ".jpg");
        writeJpeg(scaled, jpegPath, jpegQuality);

        if (!jpegPath.equals(file)) {
            Files.deleteIfExists(file);
        }
        return jpegPath.getFileName().toString();
    }

    private static BufferedImage scale(BufferedImage source, int maxDimension) {
        int width = source.getWidth();
        int height = source.getHeight();
        if (width <= maxDimension && height <= maxDimension) {
            return toRgb(source);
        }
        double ratio = Math.min((double) maxDimension / width, (double) maxDimension / height);
        int targetW = Math.max(1, (int) Math.round(width * ratio));
        int targetH = Math.max(1, (int) Math.round(height * ratio));

        BufferedImage target = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = target.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, targetW, targetH);
        g.drawImage(source, 0, 0, targetW, targetH, null);
        g.dispose();
        return target;
    }

    private static BufferedImage toRgb(BufferedImage source) {
        if (source.getType() == BufferedImage.TYPE_INT_RGB) {
            return source;
        }
        BufferedImage rgb = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
        g.drawImage(source, 0, 0, null);
        g.dispose();
        return rgb;
    }

    private static void writeJpeg(BufferedImage image, Path target, float quality) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            ImageIO.write(image, "jpg", target.toFile());
            return;
        }
        ImageWriter writer = writers.next();
        try (OutputStream os = Files.newOutputStream(target);
             ImageOutputStream ios = ImageIO.createImageOutputStream(os)) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(Math.max(0.4f, Math.min(1f, quality)));
            }
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
    }

    private static String stripExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }
}
