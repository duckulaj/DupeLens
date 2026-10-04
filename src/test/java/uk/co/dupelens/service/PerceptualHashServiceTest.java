package uk.co.dupelens.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

class PerceptualHashServiceTest {
    @TempDir Path temp;
    @Test void sameImageHasZeroDistance() throws Exception {
        Path p=temp.resolve("x.png"); BufferedImage img=new BufferedImage(100,80,BufferedImage.TYPE_INT_RGB); Graphics2D g=img.createGraphics();g.setColor(Color.WHITE);g.fillRect(0,0,100,80);g.setColor(Color.BLACK);g.fillRect(0,0,40,80);g.dispose();ImageIO.write(img,"png",p.toFile());
        PerceptualHashService s=new PerceptualHashService();String h=s.dHash(p);assertThat(s.hamming(h,h)).isZero();
    }
}
