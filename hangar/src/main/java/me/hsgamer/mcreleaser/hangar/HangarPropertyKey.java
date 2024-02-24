package me.hsgamer.mcreleaser.hangar;

import me.hsgamer.mcreleaser.core.property.PropertyKey;
import me.hsgamer.mcreleaser.core.property.PropertyPrefix;

public interface HangarPropertyKey {
    PropertyPrefix HANGAR = new PropertyPrefix("hangar");
    PropertyKey KEY = HANGAR.key("key");
    PropertyKey PROJECT = HANGAR.key("project");
    PropertyKey CHANNEL = HANGAR.key("channel");
    PropertyKey PLATFORM = HANGAR.key("platform");
    PropertyKey DEPENDENCIES = HANGAR.key("dependencies");
}
