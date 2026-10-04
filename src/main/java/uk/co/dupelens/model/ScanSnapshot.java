package uk.co.dupelens.model;

import java.time.Instant;

public record ScanSnapshot(
        boolean running, long scanId, String phase, String message, String currentFile,
        long filesSeen, long bytesSeen, int progressPercent, Instant startedAt,
        int duplicateGroups, int duplicateFiles, long reclaimableBytes,
        int similarImageGroups, int similarVideoGroups, int aiGroups,
        boolean cancelled, String error) {
    public static ScanSnapshot idle() {
        return new ScanSnapshot(false, 0, "IDLE", "Ready to scan", "", 0, 0, 0, null,
                0, 0, 0, 0, 0, 0, false, null);
    }
}
