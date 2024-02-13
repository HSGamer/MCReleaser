package me.hsgamer.mcreleaser.core.util;

public class Validate {
    public static void check(boolean state, String message) {
        if (!state) {
            throw new IllegalArgumentException(message);
        }
    }

    public static void checkNotNull(Object object, String message) {
        check(object != null, message);
    }
}
