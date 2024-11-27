package me.hsgamer.mcreleaser.renderer.bbcode;

import me.hsgamer.mcreleaser.renderer.bbcode.internal.BBCodeNodeRendererContext;
import me.hsgamer.mcreleaser.renderer.bbcode.internal.BBCodeWriter;
import me.hsgamer.mcreleaser.renderer.bbcode.internal.CodeBBCodeNodeRenderer;
import org.commonmark.internal.util.Escaping;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;

public class MarkdownToBBCodeConverter {
    private static final Parser parser = Parser.builder().build();

    // TODO: Implement a dedicated library for BBCode rendering
    public static String convert(String markdown) {
        Node document = parser.parse(markdown);
        StringBuilder builder = new StringBuilder();
        BBCodeNodeRendererContext context = new BBCodeNodeRendererContext() {
            @Override
            public String escapeUrl(String url) {
                return Escaping.escapeHtml(url);
            }

            @Override
            public BBCodeWriter getWriter() {
                return new BBCodeWriter(builder);
            }

            @Override
            public String getSoftBreak() {
                return "\n";
            }
        };
        document.accept(new CodeBBCodeNodeRenderer(context));
        return builder.toString();
    }
}
