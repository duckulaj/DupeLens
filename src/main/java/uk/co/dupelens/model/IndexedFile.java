package uk.co.dupelens.model;

public record IndexedFile(
        long id, String path, long size, long modified, MediaType mediaType,
        String partialHash, String fullHash, String perceptualHash, String videoFingerprint,
        String duplicateGroup, String similarImageGroup, String similarVideoGroup, String aiGroup,
        String aiCategory, String aiScene, String aiSubjects, String aiKeywords, String aiSummary,
        boolean aiProcessed, long lastScanId) {
    public String name() {
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return slash >= 0 ? path.substring(slash + 1) : path;
    }
    public String parent() {
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return slash > 0 ? path.substring(0, slash) : path;
    }
}
