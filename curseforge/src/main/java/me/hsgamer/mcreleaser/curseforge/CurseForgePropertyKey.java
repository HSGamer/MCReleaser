package me.hsgamer.mcreleaser.curseforge;

import me.hsgamer.mcreleaser.core.property.PropertyKey;
import me.hsgamer.mcreleaser.core.property.PropertyPrefix;

public interface CurseForgePropertyKey {
    PropertyPrefix CURSEFORGE = new PropertyPrefix("curseforge");
    PropertyKey TOKEN = CURSEFORGE.key("token");
    PropertyKey PROJECT = CURSEFORGE.key("project");
    PropertyKey TYPE = CURSEFORGE.key("type");
    PropertyKey RELEASE_TYPE = CURSEFORGE.key("releaseType");
    PropertyKey GAME_VERSIONS = CURSEFORGE.key("gameVersions");
    PropertyKey RELATIONS = CURSEFORGE.key("relations");
    PropertyKey LOADERS = CURSEFORGE.key("loaders");
    PropertyKey JAVA_VERSION = CURSEFORGE.key("javaVersion");
    PropertyKey ENVIRONMENT = CURSEFORGE.key("environment");
    PropertyKey MANUAL = CURSEFORGE.key("manual");
}
