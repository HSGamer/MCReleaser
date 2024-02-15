package me.hsgamer.mcreleaser.core.util;

public class StringUtil {
    public static String[] splitCommaOrSpace(String input) {
        return input.split(",|\\s+");
    }

    public static String[] splitSpace(String input) {
        return input.split("\\s+");
    }
}
