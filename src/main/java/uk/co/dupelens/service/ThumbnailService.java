package uk.co.dupelens.service;

import org.springframework.stereotype.Service;
import uk.co.dupelens.model.IndexedFile;
import uk.co.dupelens.model.MediaType;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;

@Service
public class ThumbnailService {
    private final VideoFingerprintService videos;
    public ThumbnailService(VideoFingerprintService videos){this.videos=videos;}

    public Path thumbnail(IndexedFile f) throws Exception {
        Path cache=Path.of("data","thumbs",f.id()+"-"+f.modified()+".jpg"); if(Files.exists(cache))return cache;
        BufferedImage src=f.mediaType()==MediaType.VIDEO?videoFrame(Path.of(f.path())):ImageIO.read(Path.of(f.path()).toFile());
        if(src==null)throw new IllegalArgumentException("Preview unavailable");
        int max=360; double scale=Math.min(1.0,(double)max/Math.max(src.getWidth(),src.getHeight()));
        int w=Math.max(1,(int)(src.getWidth()*scale)),h=Math.max(1,(int)(src.getHeight()*scale));
        BufferedImage out=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB); Graphics2D g=out.createGraphics();
        try{g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);g.drawImage(src,0,0,w,h,null);}finally{g.dispose();}
        Files.createDirectories(cache.getParent()); ImageIO.write(out,"jpg",cache.toFile()); return cache;
    }

    private BufferedImage videoFrame(Path p)throws Exception{
        if(!videos.available())return null; Process proc=new ProcessBuilder("ffmpeg","-loglevel","error","-ss","1","-i",p.toString(),"-frames:v","1","-vf","scale=640:-2","-f","image2pipe","-vcodec","png","pipe:1").start();
        byte[] b=proc.getInputStream().readAllBytes(); if(!proc.waitFor(30, TimeUnit.SECONDS)||proc.exitValue()!=0)return null; return ImageIO.read(new ByteArrayInputStream(b));
    }
}
