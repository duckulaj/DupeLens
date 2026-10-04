package uk.co.dupelens.service;

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
    private final SettingsService settings;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final JsonMapper json = JsonMapper.builder().build();

    public OllamaAiService(SettingsService settings) { this.settings = settings; }

    public boolean available() {
        try {
            AppSettings s=settings.get();
            HttpRequest req=HttpRequest.newBuilder(URI.create(s.ollamaUrl()+"/api/tags")).timeout(Duration.ofSeconds(3)).GET().build();
            return http.send(req,HttpResponse.BodyHandlers.discarding()).statusCode()/100==2;
        } catch(Exception e){ return false; }
    }

    public AiAnalysis analyse(Path image) throws Exception {
        AppSettings s=settings.get();
        String base64=Base64.getEncoder().encodeToString(resizeJpeg(image));
        String prompt="""
            Analyse this personal photograph for local photo organisation. Return ONLY valid JSON with exactly these keys:
            category (short broad category such as family, pets, travel, nature, food, event, document, vehicle, home, other),
            scene (short scene/location type), subjects (array of visible subject descriptions; do not identify unknown people),
            keywords (array of 3-10 useful search tags), summary (one short factual sentence).
            Do not infer sensitive personal traits, identities, medical conditions, religion, politics, or other private attributes.
            """;
        Map<String,Object> body=Map.of(
                "model",s.aiModel(),"stream",false,"format","json",
                "messages",List.of(Map.of("role","user","content",prompt,"images",List.of(base64))),
                "options",Map.of("temperature",0.1));
        String payload=json.writeValueAsString(body);
        HttpRequest req=HttpRequest.newBuilder(URI.create(s.ollamaUrl()+"/api/chat"))
                .timeout(Duration.ofMinutes(3)).header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload)).build();
        HttpResponse<String> res=http.send(req,HttpResponse.BodyHandlers.ofString());
        if(res.statusCode()/100!=2) throw new IllegalStateException("Ollama returned HTTP "+res.statusCode()+": "+res.body());
        JsonNode root=json.readTree(res.body());
        String content=root.path("message").path("content").asText();
        JsonNode a=json.readTree(content);
        return new AiAnalysis(text(a,"category","other"), text(a,"scene","unknown"), arrayText(a.path("subjects")),
                arrayText(a.path("keywords")), text(a,"summary",""));
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
        int max=1280; double scale=Math.min(1.0, (double)max/Math.max(src.getWidth(),src.getHeight()));
        int w=Math.max(1,(int)Math.round(src.getWidth()*scale)), h=Math.max(1,(int)Math.round(src.getHeight()*scale));
        BufferedImage out=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=out.createGraphics();
        try { g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR); g.drawImage(src,0,0,w,h,null); } finally { g.dispose(); }
        ByteArrayOutputStream baos=new ByteArrayOutputStream(); ImageIO.write(out,"jpg",baos); return baos.toByteArray();
    }
}
