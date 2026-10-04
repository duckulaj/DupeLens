package uk.co.dupelens.service;

import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Path;

@Service
public class PerceptualHashService {
    public String dHash(Path path) throws Exception {
        BufferedImage src = ImageIO.read(path.toFile());
        if (src == null) throw new IllegalArgumentException("Unsupported image format: " + path);
        BufferedImage scaled = new BufferedImage(9, 8, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = scaled.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(src, 0, 0, 9, 8, null);
        } finally { g.dispose(); }
        long bits = 0L;
        int bit = 0;
        for (int y=0;y<8;y++) {
            for (int x=0;x<8;x++) {
                int a = scaled.getRaster().getSample(x,y,0);
                int b = scaled.getRaster().getSample(x+1,y,0);
                if (a > b) bits |= (1L << bit);
                bit++;
            }
        }
        return String.format("%016x", bits);
    }

    public int hamming(String a, String b) {
        if (a == null || b == null) return Integer.MAX_VALUE;
        try { return Long.bitCount(Long.parseUnsignedLong(a,16) ^ Long.parseUnsignedLong(b,16)); }
        catch (Exception e) { return Integer.MAX_VALUE; }
    }
}
