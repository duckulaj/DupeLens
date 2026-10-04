package uk.co.dupelens.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import uk.co.dupelens.model.ScanSnapshot;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Repository
public class ScanRepository {
    private final JdbcTemplate jdbc;
    public ScanRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public long create(List<String> roots) {
        Long id = jdbc.queryForObject("INSERT INTO scan_job(started_at,status,roots) VALUES(?,?,?) RETURNING id", Long.class,
                Instant.now().toString(), "RUNNING", String.join("\n", roots));
        return id == null ? 0L : id;
    }

    public void finish(ScanSnapshot s, String status) {
        jdbc.update("""
            UPDATE scan_job SET finished_at=?,status=?,files_seen=?,bytes_seen=?,duplicate_groups=?,duplicate_files=?,
            reclaimable_bytes=?,similar_image_groups=?,similar_video_groups=?,ai_groups=?,message=? WHERE id=?
            """, Instant.now().toString(), status, s.filesSeen(), s.bytesSeen(), s.duplicateGroups(), s.duplicateFiles(),
                s.reclaimableBytes(), s.similarImageGroups(), s.similarVideoGroups(), s.aiGroups(), s.message(), s.scanId());
    }

    public long latestCompletedScanId() {
        Long id = jdbc.queryForObject("SELECT COALESCE(MAX(id),0) FROM scan_job WHERE status='COMPLETED'", Long.class);
        return id == null ? 0 : id;
    }

    public List<Map<String,Object>> history() {
        return jdbc.queryForList("SELECT * FROM scan_job ORDER BY id DESC LIMIT 50");
    }
}
