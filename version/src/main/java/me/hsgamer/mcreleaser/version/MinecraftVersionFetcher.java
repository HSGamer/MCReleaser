package me.hsgamer.mcreleaser.version;

import com.google.gson.Gson;

import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public class MinecraftVersionFetcher {
    private static final String VERSION_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    private static final AtomicReference<VersionManifest> versionManifestRef = new AtomicReference<>();

    public static CompletableFuture<VersionManifest> fetchVersionManifest() {
        if (versionManifestRef.get() != null) {
            return CompletableFuture.completedFuture(versionManifestRef.get());
        }

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(VERSION_URL))
                .build();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
                .thenApply(HttpResponse::body)
                .thenApply(inputStream -> new Gson().fromJson(new InputStreamReader(inputStream), VersionManifest.class))
                .thenApply(manifest -> {
                    versionManifestRef.set(manifest);
                    return manifest;
                });
    }

    private static boolean isTypeMatched(VersionManifest.Version version, VersionTypeFilter versionTypeFilter) {
        return switch (versionTypeFilter) {
            case ALL -> true;
            case RELEASE -> version.type().equalsIgnoreCase("release");
            case SNAPSHOT -> version.type().equalsIgnoreCase("snapshot");
        };
    }

    private static VersionManifest.Version getLatestVersion(VersionManifest versionManifest, VersionTypeFilter versionTypeFilter) {
        return switch (versionTypeFilter) {
            case RELEASE -> versionManifest.getLatestRelease();
            case SNAPSHOT, ALL -> versionManifest.getLatestSnapshot();
        };
    }

    private static String getLatestVersionId(VersionManifest versionManifest, VersionTypeFilter versionTypeFilter) {
        return switch (versionTypeFilter) {
            case RELEASE -> versionManifest.latest().release();
            case SNAPSHOT, ALL -> versionManifest.latest().snapshot();
        };
    }

    private static List<VersionManifest.Version> filterVersions(VersionManifest versionManifest, List<String> versionFilters, VersionTypeFilter versionTypeFilter) {
        List<VersionManifest.Version> versions = new ArrayList<>();
        List<VersionManifest.Version> fetchedVersions = versionManifest.versions();
        for (String versionFilter : versionFilters) {
            String[] split = versionFilter.split("-");
            if (split.length == 2) {
                String start = split[0];
                String end = split[1];

                if (end.equalsIgnoreCase("latest")) {
                    end = getLatestVersionId(versionManifest, versionTypeFilter);
                }

                int startIndex = -1;
                int endIndex = -1;

                for (int i = 0; i < fetchedVersions.size(); i++) {
                    VersionManifest.Version fetchedVersion = fetchedVersions.get(i);
                    if (fetchedVersion.id().equalsIgnoreCase(start)) {
                        startIndex = i;
                    }
                    if (fetchedVersion.id().equalsIgnoreCase(end)) {
                        endIndex = i;
                    }
                }

                if (startIndex == -1 || endIndex == -1 || startIndex < endIndex) {
                    throw new IllegalArgumentException("Invalid version range: " + versionFilter);
                }

                for (int i = startIndex; i >= endIndex; i--) {
                    VersionManifest.Version version = fetchedVersions.get(i);
                    if (isTypeMatched(version, versionTypeFilter)) {
                        versions.add(version);
                    }
                }
            } else if (versionFilter.equalsIgnoreCase("latest")) {
                versions.add(getLatestVersion(versionManifest, versionTypeFilter));
            } else {
                for (VersionManifest.Version version : fetchedVersions) {
                    if (version.id().equalsIgnoreCase(versionFilter) && isTypeMatched(version, versionTypeFilter)) {
                        versions.add(version);
                    }
                }
            }
        }

        return versions;
    }

    public static CompletableFuture<List<VersionManifest.Version>> fetchVersions(List<String> versionFilters, VersionTypeFilter versionTypeFilter) {
        return fetchVersionManifest().thenApply(versionManifest -> filterVersions(versionManifest, versionFilters, versionTypeFilter));
    }

    public static CompletableFuture<List<String>> fetchVersionIds(List<String> versionFilters, VersionTypeFilter versionTypeFilter) {
        return fetchVersions(versionFilters, versionTypeFilter).thenApply(versions -> {
            List<String> versionIds = new ArrayList<>();
            for (VersionManifest.Version version : versions) {
                versionIds.add(version.id());
            }
            return versionIds;
        });
    }
}
