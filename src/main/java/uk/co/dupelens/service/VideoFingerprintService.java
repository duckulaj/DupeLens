package uk.co.dupelens.service;

import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class VideoFingerprintService {
    private final PerceptualHashService phash;
    public VideoFingerprintService(PerceptualHashService phash) { this.phash = phash; }

    public boolean available() { return commandExists("ffmpeg") && commandExists("ffprobe"); }

    public String fingerprint(Path video) throws Exception {
        double duration = durationSeconds(video);
        if (duration <= 0) throw new IllegalArgumentException("Could not determine video duration");
        double[] positions = {0.10, 0.30, 0.50, 0.70, 0.90};
        List<String> hashes = new ArrayList<>();
        for (double p : positions) hashes.add(hashFrame(video, Math.max(0, duration * p)));
        return Math.round(duration * 1000) + "|" + String.join(",", hashes);
    }

    public double durationSeconds(Path video) throws Exception {
        Process p = new ProcessBuilder("ffprobe", "-v", "error", "-show_entries", "format=duration",
                "-of", "default=noprint_wrappers=1:nokey=1", video.toString()).redirectErrorStream(true).start();
        byte[] out = p.getInputStream().readAllBytes();
        if (!p.waitFor(20, TimeUnit.SECONDS)) { p.destroyForcibly(); throw new IllegalStateException("ffprobe timed out"); }
        return Double.parseDouble(new String(out, StandardCharsets.UTF_8).trim());
    }

    private String hashFrame(Path video, double second) throws Exception {
        Process p = new ProcessBuilder("ffmpeg", "-loglevel", "error", "-ss", String.format(java.util.Locale.ROOT,"%.3f",second),
                "-i", video.toString(), "-frames:v", "1", "-vf", "scale=9:8", "-f", "image2pipe", "-vcodec", "png", "pipe:1")
                .redirectErrorStream(false).start();
        byte[] png = p.getInputStream().readAllBytes();
        byte[] err = p.getErrorStream().readAllBytes();
        if (!p.waitFor(30, TimeUnit.SECONDS)) { p.destroyForcibly(); throw new IllegalStateException("ffmpeg timed out"); }
        if (p.exitValue()!=0 || png.length==0) throw new IllegalStateException("ffmpeg frame extraction failed: " + new String(err,StandardCharsets.UTF_8));
        BufferedImage src = ImageIO.read(new ByteArrayInputStream(png));
        if (src == null) throw new IllegalStateException("Unable to read ffmpeg frame");
        return hashImage(src);
    }

    private String hashImage(BufferedImage src) {
        BufferedImage scaled = new BufferedImage(9,8,BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g=scaled.createGraphics();
        try { g.drawImage(src,0,0,9,8,null); } finally { g.dispose(); }
        long bits=0; int bit=0;
        for(int y=0;y<8;y++) for(int x=0;x<8;x++) {
            if(scaled.getRaster().getSample(x,y,0)>scaled.getRaster().getSample(x+1,y,0)) bits|=(1L<<bit);
            bit++;
        }
        return String.format("%016x",bits);
    }

    public Parsed parse(String fp) {
        if (fp==null || !fp.contains("|")) return null;
        try {
            String[] p=fp.split("\\|",2); String[] hs=p[1].split(",");
            return new Parsed(Long.parseLong(p[0]), List.of(hs));
        } catch(Exception e){ return null; }
    }

    public boolean similar(String a, String b, int hammingThreshold, double durationTolerance) {
        Parsed x=parse(a), y=parse(b); if(x==null||y==null) return false;
        long max=Math.max(x.durationMs(),y.durationMs());
        long allowed=Math.max(3000L,(long)(max*durationTolerance));
        if(Math.abs(x.durationMs()-y.durationMs())>allowed) return false;
        int count=Math.min(x.hashes().size(),y.hashes().size()); if(count==0) return false;
        double avg=0; for(int i=0;i<count;i++) avg+=phash.hamming(x.hashes().get(i),y.hashes().get(i));
        return (avg/count)<=hammingThreshold;
    }

    private boolean commandExists(String name) {
        try { Process p=new ProcessBuilder("sh","-c","command -v "+name).start(); return p.waitFor(2,TimeUnit.SECONDS)&&p.exitValue()==0; }
        catch(Exception e){ return false; }
    }

    public record Parsed(long durationMs, List<String> hashes) {}
}
