package me.hsgamer.mcreleaser.core.property;

import java.util.Locale;
import java.util.Objects;

public class PropertyKey {
    private final String key;
    private final String env;
    private final String defaultValue;

    public PropertyKey(String key, String env, String defaultValue) {
        this.key = key;
        this.env = env;
        this.defaultValue = defaultValue;
    }

    public PropertyKey(String key, String env, Object defaultValue) {
        this(key, env, Objects.toString(defaultValue));
    }

    public PropertyKey(String key, String defaultValue) {
        this(key, camelToConstant(key), defaultValue);
    }

    public PropertyKey(String key, Object defaultValue) {
        this(key, camelToConstant(key), defaultValue);
    }

    private static String camelToConstant(String camel) {
        return camel.toUpperCase(Locale.ROOT)
                .replaceAll("([A-Z]+)", "_$1")
                .replaceAll("[-\\s]+", "_")
                .replaceFirst("^_", "")
                .replaceFirst("_$", "");
    }

    public String getValue() {
        String value = System.getenv(env);
        if (value == null) {
            value = System.getProperty(key);
        }
        if (value == null) {
            value = defaultValue;
        }
        return value;
    }

    public Number asNumber() {
        return Double.parseDouble(getValue());
    }

    public boolean asBoolean() {
        return Boolean.parseBoolean(getValue());
    }
}
