package me.hsgamer.mcreleaser.cli;

import me.hsgamer.mcreleaser.core.property.PropertyKey;

public interface CLIPropertyKey {
    PropertyKey PLATFORMS = new PropertyKey("platforms");
    PropertyKey SYNC = new PropertyKey("sync");
    PropertyKey FILES = new PropertyKey("files");
}
