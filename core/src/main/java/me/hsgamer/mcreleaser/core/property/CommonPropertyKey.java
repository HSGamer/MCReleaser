package me.hsgamer.mcreleaser.core.property;

import me.hsgamer.mcreleaser.core.util.Validate;

public interface CommonPropertyKey {
    PropertyKey NAME = new PropertyKey("name");
    PropertyKey VERSION = new PropertyKey("version");
    PropertyKey DESCRIPTION = new PropertyKey("description");

    static void checkPresent() {
        Validate.check(NAME.isPresent(), "name is not present");
        Validate.check(VERSION.isPresent(), "version is not present");
        Validate.check(DESCRIPTION.isPresent(), "description is not present");
    }
}
