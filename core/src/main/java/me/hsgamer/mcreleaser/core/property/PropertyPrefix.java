package me.hsgamer.mcreleaser.core.property;

import me.hsgamer.mcreleaser.core.util.Validate;

public class PropertyPrefix {
    private final String prefix;

    public PropertyPrefix(String prefix) {
        this.prefix = prefix;
    }

    public PropertyKey key(String key) {
        Validate.check(!key.isEmpty(), "Key cannot be empty");
        key = key.substring(0, 1).toUpperCase() + key.substring(1);
        return new PropertyKey(prefix + key);
    }

    public PropertyKey key(String key, String env) {
        Validate.check(!key.isEmpty(), "Key cannot be empty");
        Validate.check(!env.isEmpty(), "Env cannot be empty");
        key = key.substring(0, 1).toUpperCase() + key.substring(1);
        return new PropertyKey(prefix + key, prefix.toUpperCase() + "_" + env);
    }
}
