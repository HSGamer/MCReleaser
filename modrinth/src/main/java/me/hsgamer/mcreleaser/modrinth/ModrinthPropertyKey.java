package me.hsgamer.mcreleaser.modrinth;

import me.hsgamer.mcreleaser.core.property.PropertyKey;
import me.hsgamer.mcreleaser.core.property.PropertyPrefix;

public interface ModrinthPropertyKey {
    PropertyPrefix MODRINTH = new PropertyPrefix("modrinth");
    PropertyKey TOKEN = MODRINTH.key("token");
    PropertyKey PROJECT = MODRINTH.key("project");
    PropertyKey VERSION_TYPE = MODRINTH.key("versionType");
    PropertyKey FEATURED = MODRINTH.key("featured");
    PropertyKey UNFEATURE = MODRINTH.key("unfeature");
    PropertyKey LOADERS = MODRINTH.key("loaders");
    PropertyKey DEPENDENCIES = MODRINTH.key("dependencies");
}
