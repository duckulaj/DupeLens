PRAGMA journal_mode=WAL;
PRAGMA busy_timeout=5000;

CREATE TABLE IF NOT EXISTS scan_job (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    started_at TEXT NOT NULL,
    finished_at TEXT,
    status TEXT NOT NULL,
    roots TEXT NOT NULL,
    files_seen INTEGER DEFAULT 0,
    bytes_seen INTEGER DEFAULT 0,
    duplicate_groups INTEGER DEFAULT 0,
    duplicate_files INTEGER DEFAULT 0,
    reclaimable_bytes INTEGER DEFAULT 0,
    similar_image_groups INTEGER DEFAULT 0,
    similar_video_groups INTEGER DEFAULT 0,
    ai_groups INTEGER DEFAULT 0,
    message TEXT
);

CREATE TABLE IF NOT EXISTS file_entry (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    path TEXT NOT NULL UNIQUE,
    size INTEGER NOT NULL,
    modified INTEGER NOT NULL,
    media_type TEXT NOT NULL,
    partial_hash TEXT,
    full_hash TEXT,
    perceptual_hash TEXT,
    video_fingerprint TEXT,
    duplicate_group TEXT,
    similar_image_group TEXT,
    similar_video_group TEXT,
    ai_group TEXT,
    ai_category TEXT,
    ai_scene TEXT,
    ai_subjects TEXT,
    ai_keywords TEXT,
    ai_summary TEXT,
    ai_processed INTEGER DEFAULT 0,
    last_scan_id INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_file_scan ON file_entry(last_scan_id);
CREATE INDEX IF NOT EXISTS idx_file_size ON file_entry(last_scan_id, size);
CREATE INDEX IF NOT EXISTS idx_file_dupe ON file_entry(last_scan_id, duplicate_group);
CREATE INDEX IF NOT EXISTS idx_file_img ON file_entry(last_scan_id, similar_image_group);
CREATE INDEX IF NOT EXISTS idx_file_vid ON file_entry(last_scan_id, similar_video_group);
CREATE INDEX IF NOT EXISTS idx_file_ai ON file_entry(last_scan_id, ai_group);

CREATE TABLE IF NOT EXISTS app_setting (
    setting_key TEXT PRIMARY KEY,
    setting_value TEXT NOT NULL
);
