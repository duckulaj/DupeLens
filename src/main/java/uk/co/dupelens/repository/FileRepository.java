package uk.co.dupelens.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import uk.co.dupelens.model.AiAnalysis;
import uk.co.dupelens.model.IndexedFile;
import uk.co.dupelens.model.MediaType;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

@Repository
public class FileRepository {
    private final JdbcTemplate jdbc;
    private final RowMapper<IndexedFile> mapper = this::map;

    public FileRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void upsertDiscovered(String path, long size, long modified, MediaType mediaType, long scanId) {
        jdbc.update("""
            INSERT INTO file_entry(path,size,modified,media_type,last_scan_id)
            VALUES(?,?,?,?,?)
            ON CONFLICT(path) DO UPDATE SET
              partial_hash=CASE WHEN file_entry.size=excluded.size AND file_entry.modified=excluded.modified THEN file_entry.partial_hash ELSE NULL END,
              full_hash=CASE WHEN file_entry.size=excluded.size AND file_entry.modified=excluded.modified THEN file_entry.full_hash ELSE NULL END,
              perceptual_hash=CASE WHEN file_entry.size=excluded.size AND file_entry.modified=excluded.modified THEN file_entry.perceptual_hash ELSE NULL END,
              video_fingerprint=CASE WHEN file_entry.size=excluded.size AND file_entry.modified=excluded.modified THEN file_entry.video_fingerprint ELSE NULL END,
              ai_category=CASE WHEN file_entry.size=excluded.size AND file_entry.modified=excluded.modified THEN file_entry.ai_category ELSE NULL END,
              ai_scene=CASE WHEN file_entry.size=excluded.size AND file_entry.modified=excluded.modified THEN file_entry.ai_scene ELSE NULL END,
              ai_subjects=CASE WHEN file_entry.size=excluded.size AND file_entry.modified=excluded.modified THEN file_entry.ai_subjects ELSE NULL END,
              ai_keywords=CASE WHEN file_entry.size=excluded.size AND file_entry.modified=excluded.modified THEN file_entry.ai_keywords ELSE NULL END,
              ai_summary=CASE WHEN file_entry.size=excluded.size AND file_entry.modified=excluded.modified THEN file_entry.ai_summary ELSE NULL END,
              ai_processed=CASE WHEN file_entry.size=excluded.size AND file_entry.modified=excluded.modified THEN file_entry.ai_processed ELSE 0 END,
              size=excluded.size, modified=excluded.modified, media_type=excluded.media_type, last_scan_id=excluded.last_scan_id,
              duplicate_group=NULL, similar_image_group=NULL, similar_video_group=NULL, ai_group=NULL
            """, path, size, modified, mediaType.name(), scanId);
    }

    public List<IndexedFile> findByScan(long scanId) {
        return jdbc.query("SELECT * FROM file_entry WHERE last_scan_id=? ORDER BY path", mapper, scanId);
    }

    public List<IndexedFile> findByScanAndType(long scanId, MediaType type) {
        return jdbc.query("SELECT * FROM file_entry WHERE last_scan_id=? AND media_type=? ORDER BY path", mapper, scanId, type.name());
    }

    public Optional<IndexedFile> findById(long id) {
        List<IndexedFile> rows = jdbc.query("SELECT * FROM file_entry WHERE id=?", mapper, id);
        return rows.stream().findFirst();
    }

    public List<IndexedFile> findByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        String marks = String.join(",", Collections.nCopies(ids.size(), "?"));
        return jdbc.query("SELECT * FROM file_entry WHERE id IN (" + marks + ") ORDER BY path", mapper, ids.toArray());
    }

    public void updateHashes(long id, String partialHash, String fullHash) {
        jdbc.update("UPDATE file_entry SET partial_hash=COALESCE(?,partial_hash), full_hash=COALESCE(?,full_hash) WHERE id=?",
                partialHash, fullHash, id);
    }

    public void updatePartialHash(long id, String partialHash) {
        jdbc.update("UPDATE file_entry SET partial_hash=? WHERE id=?", partialHash, id);
    }

    public void updateFullHash(long id, String fullHash) {
        jdbc.update("UPDATE file_entry SET full_hash=? WHERE id=?", fullHash, id);
    }

    public void updatePerceptualHash(long id, String hash) {
        jdbc.update("UPDATE file_entry SET perceptual_hash=? WHERE id=?", hash, id);
    }

    public void updateVideoFingerprint(long id, String fingerprint) {
        jdbc.update("UPDATE file_entry SET video_fingerprint=? WHERE id=?", fingerprint, id);
    }

    public void clearGroups(long scanId) {
        jdbc.update("UPDATE file_entry SET duplicate_group=NULL, similar_image_group=NULL, similar_video_group=NULL, ai_group=NULL WHERE last_scan_id=?", scanId);
    }

    public void setDuplicateGroup(Collection<Long> ids, String group) { setGroup(ids, "duplicate_group", group); }
    public void setSimilarImageGroup(Collection<Long> ids, String group) { setGroup(ids, "similar_image_group", group); }
    public void setSimilarVideoGroup(Collection<Long> ids, String group) { setGroup(ids, "similar_video_group", group); }
    public void setAiGroup(Collection<Long> ids, String group) { setGroup(ids, "ai_group", group); }

    private void setGroup(Collection<Long> ids, String column, String group) {
        if (ids == null || ids.isEmpty()) return;
        String marks = String.join(",", Collections.nCopies(ids.size(), "?"));
        List<Object> args = new ArrayList<>();
        args.add(group);
        args.addAll(ids);
        jdbc.update("UPDATE file_entry SET " + column + "=? WHERE id IN (" + marks + ")", args.toArray());
    }

    public void updateAi(long id, AiAnalysis a) {
        jdbc.update("""
            UPDATE file_entry SET ai_category=?, ai_scene=?, ai_subjects=?, ai_keywords=?, ai_summary=?, ai_processed=1 WHERE id=?
            """, a.category(), a.scene(), a.subjects(), a.keywords(), a.summary(), id);
    }

    public void updateAiByFullHash(long scanId, String fullHash, AiAnalysis a) {
        if (fullHash == null || fullHash.isBlank()) return;
        jdbc.update("""
            UPDATE file_entry SET ai_category=?, ai_scene=?, ai_subjects=?, ai_keywords=?, ai_summary=?, ai_processed=1
            WHERE last_scan_id=? AND full_hash=?
            """, a.category(), a.scene(), a.subjects(), a.keywords(), a.summary(), scanId, fullHash);
    }

    public void deleteRows(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) return;
        String marks = String.join(",", Collections.nCopies(ids.size(), "?"));
        jdbc.update("DELETE FROM file_entry WHERE id IN (" + marks + ")", ids.toArray());
    }

    private IndexedFile map(ResultSet rs, int rowNum) throws SQLException {
        return new IndexedFile(
                rs.getLong("id"), rs.getString("path"), rs.getLong("size"), rs.getLong("modified"),
                MediaType.valueOf(rs.getString("media_type")), rs.getString("partial_hash"), rs.getString("full_hash"),
                rs.getString("perceptual_hash"), rs.getString("video_fingerprint"), rs.getString("duplicate_group"),
                rs.getString("similar_image_group"), rs.getString("similar_video_group"), rs.getString("ai_group"),
                rs.getString("ai_category"), rs.getString("ai_scene"), rs.getString("ai_subjects"),
                rs.getString("ai_keywords"), rs.getString("ai_summary"), rs.getInt("ai_processed") != 0,
                rs.getLong("last_scan_id"));
    }
}
