package me.hsgamer.mcreleaser.core.property;

import me.hsgamer.mcreleaser.core.util.Validate;

public interface CommonPropertyKey {
    PropertyKey NAME = new PropertyKey("name");
    PropertyKey VERSION = new PropertyKey("version");
    PropertyKey DESCRIPTION = new PropertyKey("description");
    PropertyKey ANNOUCE_MISSING_KEY = new PropertyKey("announceMissingKey");

    static void checkPresent() {
        Validate.check(NAME.isPresent(), "name is not present");
        Validate.check(VERSION.isPresent(), "version is not present");
        Validate.check(DESCRIPTION.isPresent(), "description is not present");
    }
}
