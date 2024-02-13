package me.hsgamer.mcreleaser.core.property;

import java.util.Locale;

public class PropertyKey {
    private final String key;
    private final String env;

    public PropertyKey(String key, String env) {
        this.key = key;
        this.env = env;
    }

    public PropertyKey(String key) {
        this(key, camelToConstant(key));
    }

    private static String camelToConstant(String camel) {
        return camel.toUpperCase(Locale.ROOT)
                .replaceAll("([A-Z]+)", "_$1")
                .replaceAll("[-\\s]+", "_")
                .replaceFirst("^_", "")
                .replaceFirst("_$", "");
    }

    public boolean isPresent() {
        return getValue() != null;
    }

    public String getValue() {
        String value = System.getenv(env);
        if (value == null) {
            value = System.getProperty(key);
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
