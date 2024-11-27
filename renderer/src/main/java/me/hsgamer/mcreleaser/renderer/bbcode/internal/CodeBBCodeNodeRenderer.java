package me.hsgamer.mcreleaser.renderer.bbcode.internal;

import org.commonmark.node.*;
import org.commonmark.renderer.NodeRenderer;

import java.util.Set;

public class CodeBBCodeNodeRenderer extends AbstractVisitor implements NodeRenderer {
    private final BBCodeNodeRendererContext context;
    private final BBCodeWriter writer;

    public CodeBBCodeNodeRenderer(BBCodeNodeRendererContext context) {
        this.context = context;
        this.writer = context.getWriter();
    }

    @Override
    public Set<Class<? extends Node>> getNodeTypes() {
        return Set.of(
                Document.class,
                Heading.class,
                Paragraph.class,
                BlockQuote.class,
                BulletList.class,
                FencedCodeBlock.class,
                HtmlBlock.class,
                ThematicBreak.class,
                IndentedCodeBlock.class,
                Link.class,
                ListItem.class,
                OrderedList.class,
                Image.class,
                Emphasis.class,
                StrongEmphasis.class,
                Text.class,
                Code.class,
                HtmlInline.class,
                SoftLineBreak.class,
                HardLineBreak.class
        );
    }

    @Override
    public void render(Node node) {
        node.accept(this);
    }

    @Override
    public void visit(Document document) {
        visitChildren(document);
    }

    @Override
    public void visit(Heading heading) {
        int size = Math.max(1, 8 - heading.getLevel());
        writer.tag("b");
        writer.tag("size", String.valueOf(size));
        visitChildren(heading);
        writer.tag("/size");
        writer.tag("/b");
        writer.line();
    }

    @Override
    public void visit(Paragraph paragraph) {
        visitChildren(paragraph);
        writer.line();
    }

    @Override
    public void visit(BlockQuote blockQuote) {
        writer.tag("quote");
        visitChildren(blockQuote);
        writer.tag("/quote");
        writer.line();
    }

    @Override
    public void visit(BulletList bulletList) {
        writer.tag("list");
        visitChildren(bulletList);
        writer.tag("/list");
        writer.line();
    }

    @Override
    public void visit(FencedCodeBlock fencedCodeBlock) {
        writer.tag("code");
        writer.text(fencedCodeBlock.getLiteral());
        writer.tag("/code");
        writer.line();
    }

    @Override
    public void visit(HtmlBlock htmlBlock) {
        writer.text(htmlBlock.getLiteral());
        writer.line();
    }

    @Override
    public void visit(ThematicBreak thematicBreak) {
        writer.tag("hr");
        writer.line();
    }

    @Override
    public void visit(IndentedCodeBlock indentedCodeBlock) {
        writer.tag("code");
        writer.text(indentedCodeBlock.getLiteral());
        writer.tag("/code");
        writer.line();
    }

    @Override
    public void visit(Link link) {
        writer.tag("url", context.escapeUrl(link.getDestination()));
        visitChildren(link);
        writer.tag("/url");
    }

    @Override
    public void visit(ListItem listItem) {
        writer.tag("*");
        visitChildren(listItem);
        writer.line();
    }

    @Override
    public void visit(OrderedList orderedList) {
        writer.tag("list", orderedList.getMarkerStartNumber() + "");
        visitChildren(orderedList);
        writer.tag("/list");
        writer.line();
    }

    @Override
    public void visit(Image image) {
        writer.tag("img", context.escapeUrl(image.getDestination()));
        writer.tag("/img");
    }

    @Override
    public void visit(Emphasis emphasis) {
        writer.tag("i");
        visitChildren(emphasis);
        writer.tag("/i");
    }

    @Override
    public void visit(StrongEmphasis strongEmphasis) {
        writer.tag("b");
        writer.tag("i");
        visitChildren(strongEmphasis);
        writer.tag("/i");
        writer.tag("/b");
    }

    @Override
    public void visit(Text text) {
        writer.text(text.getLiteral());
    }

    @Override
    public void visit(Code code) {
        writer.tag("code");
        writer.text(code.getLiteral());
        writer.tag("/code");
    }

    @Override
    public void visit(HtmlInline htmlInline) {
        writer.text(htmlInline.getLiteral());
    }

    @Override
    public void visit(SoftLineBreak softLineBreak) {
        writer.raw(context.getSoftBreak());
    }

    @Override
    public void visit(HardLineBreak hardLineBreak) {
        writer.line();
    }
}
