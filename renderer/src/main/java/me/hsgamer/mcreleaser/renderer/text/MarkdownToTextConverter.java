package me.hsgamer.mcreleaser.renderer.text;

import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.text.TextContentRenderer;

public class MarkdownToTextConverter {
    private static final Parser parser = Parser.builder().build();
    private static final TextContentRenderer renderer = TextContentRenderer.builder().build();

    public static String convert(String markdown) {
        Node document = parser.parse(markdown);
        return renderer.render(document);
    }
}
