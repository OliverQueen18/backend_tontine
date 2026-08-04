package com.tontinemarche.util;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Locale;

/** Réduit le poids des signatures canvas (base64) avant stockage en base. */
public final class SignatureUtil {

    private static final int MAX_WIDTH = 600;
    private static final int MAX_HEIGHT = 280;
    private static final int KEEP_IF_SMALLER_BYTES = 24_000;

    private SignatureUtil() {
    }

    public static String compress(String dataUrl) {
        if (dataUrl == null || dataUrl.isBlank()) {
            return dataUrl;
        }
        String trimmed = dataUrl.trim();
        int comma = trimmed.indexOf(',');
        if (comma < 0) {
            return trimmed;
        }
        String meta = trimmed.substring(0, comma);
        String payload = trimmed.substring(comma + 1);
        byte[] raw = Base64.getDecoder().decode(payload);
        if (raw.length <= KEEP_IF_SMALLER_BYTES) {
            return trimmed;
        }

        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(raw));
            if (source == null) {
                return trimmed;
            }
            BufferedImage scaled = scale(source);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(scaled, "jpg", out);
            String encoded = Base64.getEncoder().encodeToString(out.toByteArray());
            return "data:image/jpeg;base64," + encoded;
        } catch (Exception e) {
            return trimmed;
        }
    }

    private static BufferedImage scale(BufferedImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        double ratio = Math.min(1.0,
                Math.min((double) MAX_WIDTH / width, (double) MAX_HEIGHT / height));
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
}
