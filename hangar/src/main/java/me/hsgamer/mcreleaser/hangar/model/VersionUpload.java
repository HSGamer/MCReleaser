package me.hsgamer.mcreleaser.hangar.model;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public record VersionUpload(String version, Map<Platform, List<PluginDependency>> pluginDependencies,
                            Map<Platform, List<String>> platformDependencies, String description,
                            List<MultipartFileOrUrl> files, String channel) {

    public enum Platform {
        PAPER,
        WATERFALL,
        VELOCITY
    }

    public record PluginDependency(String name, boolean required, @Nullable String externalUrl) {
    }

    public record MultipartFileOrUrl(List<Platform> platforms, @Nullable String externalUrl) {
    }
}
