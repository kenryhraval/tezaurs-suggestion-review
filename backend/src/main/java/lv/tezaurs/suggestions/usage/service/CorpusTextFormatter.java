package lv.tezaurs.suggestions.usage.service;

import org.springframework.web.util.HtmlUtils;

public final class CorpusTextFormatter {
    private CorpusTextFormatter() {
    }

    public static String format(String text) {
        return HtmlUtils.htmlUnescape(text)
                .replaceAll("(?i)</?(?:p|s|doc|g)(?:\\s[^>]*)?\\s*/?>", " ")
                .replaceAll("[\\s\\u00a0]+", " ");
    }
}
