package uk.co.dupelens.model;

public record AppSettings(
        int imageHammingThreshold,
        int videoHammingThreshold,
        double videoDurationTolerance,
        String ollamaUrl,
        String aiModel,
        String preferredPaths,
        String exclusions,
        long minFileSizeBytes,
        long maxFileSizeBytes,
        String keeperStrategy) {
}
