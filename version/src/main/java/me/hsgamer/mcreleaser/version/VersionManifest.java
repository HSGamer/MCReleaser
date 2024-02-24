package me.hsgamer.mcreleaser.version;

import java.util.Comparator;
import java.util.Date;
import java.util.List;

public record VersionManifest(Latest latest, List<Version> versions) {
    public Version getLatestRelease() {
        String version = latest.release();
        return versions.stream()
                .filter(v -> v.id().equals(version))
                .max(Comparator.comparing(Version::releaseTime))
                .orElseThrow(() -> new IllegalStateException("No release version found"));
    }

    public Version getLatestSnapshot() {
        String version = latest.snapshot();
        return versions.stream()
                .filter(v -> v.id().equals(version))
                .max(Comparator.comparing(Version::releaseTime))
                .orElseThrow(() -> new IllegalStateException("No snapshot version found"));
    }

    public record Latest(String release, String snapshot) {
    }

    public record Version(
            String id,
            String type,
            String url,
            Date time,
            Date releaseTime,
            String sha1,
            int complianceLevel
    ) {
    }
}
