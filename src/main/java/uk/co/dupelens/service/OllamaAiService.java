package uk.co.dupelens.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import uk.co.dupelens.model.AiAnalysis;
import uk.co.dupelens.model.AppSettings;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
public class OllamaAiService {
    private static final int MAX_IMAGE_DIMENSION = 768;
    private final SettingsService settings;
    private final MeterRegistry meters;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final JsonMapper json = JsonMapper.builder().build();

    public OllamaAiService(SettingsService settings, MeterRegistry meters) {
        this.settings = settings;
        this.meters = meters;
    }

    public boolean available() {
        Timer.Sample sample=Timer.start(meters);
        try {
            AppSettings s=settings.get();
            HttpRequest req=HttpRequest.newBuilder(URI.create(s.ollamaUrl()+"/api/tags")).timeout(Duration.ofSeconds(3)).GET().build();
            return http.send(req,HttpResponse.BodyHandlers.discarding()).statusCode()/100==2;
        } catch(Exception e){ return false; }
        finally { sample.stop(meters.timer("dupelens.ai.ollama.availability")); }
    }

    public AiAnalysis analyse(Path image) throws Exception {
        AppSettings s=settings.get();
        String base64=timed("dupelens.ai.image.prepare",
                ()->Base64.getEncoder().encodeToString(resizeJpeg(image)));
        String prompt="""
            Describe this photo for local organisation. Return only JSON with exactly these keys:
            category (one broad label: family, pets, travel, nature, food, event, document, vehicle, home, other),
            scene (short scene type), subjects (brief visible subjects; never identify people),
            keywords (3-10 short tags), summary (one short factual sentence).
            Do not infer identity or sensitive personal traits.
            """;
        Map<String,Object> body=Map.of(
                "model",s.aiModel(),"stream",false,"format","json","keep_alive","10m",
                "messages",List.of(Map.of("role","user","content",prompt,"images",List.of(base64))),
                "options",Map.of("temperature",0.1,"num_predict",256));
        String payload=json.writeValueAsString(body);
        HttpRequest req=HttpRequest.newBuilder(URI.create(s.ollamaUrl()+"/api/chat"))
                .timeout(Duration.ofMinutes(3)).header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload)).build();
        HttpResponse<String> res=timed("dupelens.ai.ollama.request",
                ()->http.send(req,HttpResponse.BodyHandlers.ofString()));
        if(res.statusCode()/100!=2) throw new IllegalStateException("Ollama returned HTTP "+res.statusCode()+": "+res.body());
        return timed("dupelens.ai.response.parse",()->{
            JsonNode root=json.readTree(res.body());
            String content=root.path("message").path("content").asText();
            JsonNode a=json.readTree(content);
            return new AiAnalysis(text(a,"category","other"), text(a,"scene","unknown"), arrayText(a.path("subjects")),
                    arrayText(a.path("keywords")), text(a,"summary",""));
        });
    }

    private <T> T timed(String metric,TimedOperation<T> operation) throws Exception {
        Timer.Sample sample=Timer.start(meters);
        try { return operation.run(); }
        finally { sample.stop(meters.timer(metric)); }
    }

    @FunctionalInterface
    private interface TimedOperation<T> {
        T run() throws Exception;
    }

    private String text(JsonNode n,String k,String d){ String v=n.path(k).asText(); return v==null||v.isBlank()?d:v.trim(); }
    private String arrayText(JsonNode n){
        if(n==null||n.isMissingNode()) return "";
        if(n.isArray()) { StringBuilder b=new StringBuilder(); for(JsonNode x:n){ if(!b.isEmpty())b.append(", "); b.append(x.asText()); } return b.toString(); }
        return n.asText("");
    }

    private byte[] resizeJpeg(Path path) throws Exception {
        BufferedImage src=ImageIO.read(path.toFile());
        if(src==null) throw new IllegalArgumentException("AI preview unsupported for "+path);
        double scale=Math.min(1.0, (double)MAX_IMAGE_DIMENSION/Math.max(src.getWidth(),src.getHeight()));
        int w=Math.max(1,(int)Math.round(src.getWidth()*scale)), h=Math.max(1,(int)Math.round(src.getHeight()*scale));
        BufferedImage out=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=out.createGraphics();
        try { g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR); g.drawImage(src,0,0,w,h,null); } finally { g.dispose(); }
        ByteArrayOutputStream baos=new ByteArrayOutputStream(); ImageIO.write(out,"jpg",baos); return baos.toByteArray();
    }
}
