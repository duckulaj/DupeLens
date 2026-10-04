package uk.co.dupelens.service;

import org.springframework.stereotype.Service;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class SystemStatusService {
    private final OllamaAiService ai;
    public SystemStatusService(OllamaAiService ai){this.ai=ai;}
    public Map<String,Boolean> status(){Map<String,Boolean> m=new LinkedHashMap<>();m.put("ffmpeg",cmd("ffmpeg"));m.put("ffprobe",cmd("ffprobe"));m.put("gio Trash",cmd("gio"));m.put("xdg-open",cmd("xdg-open"));m.put("Ollama",ai.available());return m;}
    private boolean cmd(String c){try{Process p=new ProcessBuilder("sh","-c","command -v "+c).start();return p.waitFor(2, TimeUnit.SECONDS)&&p.exitValue()==0;}catch(Exception e){return false;}}
}
