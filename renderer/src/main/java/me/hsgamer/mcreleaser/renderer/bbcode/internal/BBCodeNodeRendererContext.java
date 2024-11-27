package me.hsgamer.mcreleaser.renderer.bbcode.internal;

public interface BBCodeNodeRendererContext {
    String escapeUrl(String url);

    BBCodeWriter getWriter();

    String getSoftBreak();
}
