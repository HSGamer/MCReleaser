package me.hsgamer.mcreleaser.renderer.bbcode.internal;

import org.commonmark.internal.util.Escaping;

import java.io.IOException;
import java.util.Map;

public class BBCodeWriter {
    private static final Map<String, String> NO_ATTRIBUTES = Map.of();

    private final Appendable appendable;
    private char lastChar = 0;

    public BBCodeWriter(Appendable appendable) {
        this.appendable = appendable;
    }

    public void raw(String s) {
        append(s);
    }

    public void text(String text) {
        append(Escaping.escapeHtml(text));
    }

    public void tag(String name) {
        tag(name, null, NO_ATTRIBUTES, false);
    }

    public void tag(String name, String value) {
        tag(name, value, NO_ATTRIBUTES, false);
    }

    public void tag(String name, Map<String, String> attrs) {
        tag(name, null, attrs, false);
    }

    public void tag(String name, String value, Map<String, String> attrs, boolean voidElement) {
        append("[");
        append(name);
        if (value != null) {
            append("=");
            append(value);
        }
        if (attrs != null && !attrs.isEmpty()) {
            for (Map.Entry<String, String> entry : attrs.entrySet()) {
                append(" ");
                append(entry.getKey());
                append("=");
                append(entry.getValue());
            }
        }
        if (voidElement) {
            append("/");
        }
        append("]");
    }

    public void line() {
        if (lastChar != 0 && lastChar != '\n') {
            append("\n");
        }
    }

    protected void append(String s) {
        try {
            appendable.append(s);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        int length = s.length();
        if (length != 0) {
            lastChar = s.charAt(length - 1);
        }
    }
}
