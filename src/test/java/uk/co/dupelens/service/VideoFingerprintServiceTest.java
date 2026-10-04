package uk.co.dupelens.service;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class VideoFingerprintServiceTest {
    @Test void comparesDurationAndSampledHashes() {
        VideoFingerprintService s=new VideoFingerprintService(new PerceptualHashService());
        String a="60000|0000000000000000,0000000000000000,0000000000000000,0000000000000000,0000000000000000";
        String b="61000|0000000000000001,0000000000000000,0000000000000000,0000000000000000,0000000000000000";
        assertThat(s.similar(a,b,10,0.03)).isTrue();
    }
}
