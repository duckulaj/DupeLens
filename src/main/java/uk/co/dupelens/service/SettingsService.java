package uk.co.dupelens.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import uk.co.dupelens.model.AppSettings;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class SettingsService {
    private final JdbcTemplate jdbc;

    private static final Map<String,String> DEFAULTS = new LinkedHashMap<>();
    static {
        DEFAULTS.put("image.hamming", "8");
        DEFAULTS.put("video.hamming", "10");
        DEFAULTS.put("video.durationTolerance", "0.03");
        DEFAULTS.put("ollama.url", "http://127.0.0.1:11434");
        DEFAULTS.put("ai.model", "gemma3:4b");
        DEFAULTS.put("preferred.paths", System.getProperty("user.home") + "/Pictures");
        DEFAULTS.put("scan.exclusions", ".git\ntarget\nnode_modules\n.cache\n@eaDir\n.Trash-1000");
        DEFAULTS.put("scan.minFileSizeBytes", "1");
        DEFAULTS.put("scan.maxFileSizeBytes", "0");
        DEFAULTS.put("keeper.strategy", "PREFERRED");
    }

    public SettingsService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private void ensureDefaults() {
        DEFAULTS.forEach((k,v) -> jdbc.update("INSERT OR IGNORE INTO app_setting(setting_key,setting_value) VALUES(?,?)", k, v));
    }

    public AppSettings get() {
        ensureDefaults();
        Map<String,String> m = new LinkedHashMap<>(DEFAULTS);
        jdbc.query("SELECT setting_key,setting_value FROM app_setting", (org.springframework.jdbc.core.ResultSetExtractor<Map<String, String>>) rs -> {
            while (rs.next()) {
                m.put(rs.getString(1), rs.getString(2));
            }
            return m;
        });
        return new AppSettings(
                intVal(m,"image.hamming",8), intVal(m,"video.hamming",10), doubleVal(m,"video.durationTolerance",0.03),
                m.get("ollama.url"), m.get("ai.model"), m.get("preferred.paths"), m.get("scan.exclusions"),
                longVal(m,"scan.minFileSizeBytes",1), longVal(m,"scan.maxFileSizeBytes",0), m.get("keeper.strategy"));
    }

    public void save(AppSettings s) {
        ensureDefaults();
        put("image.hamming", Integer.toString(Math.max(0, Math.min(32, s.imageHammingThreshold()))));
        put("video.hamming", Integer.toString(Math.max(0, Math.min(32, s.videoHammingThreshold()))));
        put("video.durationTolerance", Double.toString(Math.max(0, Math.min(0.5, s.videoDurationTolerance()))));
        put("ollama.url", s.ollamaUrl().trim());
        put("ai.model", s.aiModel().trim());
        put("preferred.paths", s.preferredPaths() == null ? "" : s.preferredPaths());
        put("scan.exclusions", s.exclusions() == null ? "" : s.exclusions());
        put("scan.minFileSizeBytes", Long.toString(Math.max(0, s.minFileSizeBytes())));
        put("scan.maxFileSizeBytes", Long.toString(Math.max(0, s.maxFileSizeBytes())));
        String strategy = s.keeperStrategy() == null ? "PREFERRED" : s.keeperStrategy().toUpperCase(java.util.Locale.ROOT);
        if (!java.util.Set.of("PREFERRED","OLDEST","NEWEST","SHORTEST").contains(strategy)) strategy = "PREFERRED";
        put("keeper.strategy", strategy);
    }

    private void put(String k,String v) { jdbc.update("INSERT INTO app_setting(setting_key,setting_value) VALUES(?,?) ON CONFLICT(setting_key) DO UPDATE SET setting_value=excluded.setting_value", k,v); }
    private int intVal(Map<String,String> m,String k,int d){ try{return Integer.parseInt(m.get(k));}catch(Exception e){return d;} }
    private long longVal(Map<String,String> m,String k,long d){ try{return Long.parseLong(m.get(k));}catch(Exception e){return d;} }
    private double doubleVal(Map<String,String> m,String k,double d){ try{return Double.parseDouble(m.get(k));}catch(Exception e){return d;} }
}
