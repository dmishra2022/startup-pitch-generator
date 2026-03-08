package com.startup.pitch.service;

import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.stereotype.Service;

@Service
public class MarkdownService {

    private final Parser parser;
    private final HtmlRenderer renderer;

    public MarkdownService() {
        this.parser = Parser.builder().build();
        this.renderer = HtmlRenderer.builder().build();
    }

    public String render(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }
        // Handle Marp-specific syntax if needed, for now just basic conversion
        // Marp decks often start with frontmatter, which commonmark might not handle
        // perfectly
        // but it's a good baseline.

        // Strip Marp frontmatter if present for cleaner HTML view
        String content = markdown;
        if (content.startsWith("---")) {
            int secondDash = content.indexOf("---", 3);
            if (secondDash != -1) {
                content = content.substring(secondDash + 3).strip();
            }
        }

        // Re-insert hr for slide separators
        content = content.replace("---", "\n***\n");

        Node document = parser.parse(content);
        return renderer.render(document);
    }
}
