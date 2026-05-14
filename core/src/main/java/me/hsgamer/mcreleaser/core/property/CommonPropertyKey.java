package me.hsgamer.mcreleaser.core.property;

public interface CommonPropertyKey {
    PropertyKey PROJECT = new PropertyKey("project");
    PropertyKey NAME = new PropertyKey("name");
    PropertyKey VERSION = new PropertyKey("version");
    PropertyKey DESCRIPTION = new PropertyKey("description");
    PropertyKey GAME_VERSIONS = new PropertyKey("gameVersions");
    PropertyKey GAME_VERSION_TYPE = new PropertyKey("gameVersionType");
    PropertyKey ANNOUNCE_MISSING_KEY = new PropertyKey("announceMissingKey");
}
