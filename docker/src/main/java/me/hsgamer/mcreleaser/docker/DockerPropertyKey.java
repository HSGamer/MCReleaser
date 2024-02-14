package me.hsgamer.mcreleaser.docker;

import me.hsgamer.mcreleaser.core.property.PropertyKey;
import me.hsgamer.mcreleaser.core.property.PropertyPrefix;

public interface DockerPropertyKey {
    PropertyPrefix DOCKER = new PropertyPrefix("docker");
    PropertyKey SYNC = DOCKER.key("sync");
    PropertyKey PRIMARY_GLOB = DOCKER.key("primary");
    PropertyKey SECONDARY_GLOB = DOCKER.key("secondary");
}
