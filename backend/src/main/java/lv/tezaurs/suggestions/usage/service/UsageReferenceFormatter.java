package lv.tezaurs.suggestions.usage.service;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lv.tezaurs.suggestions.usage.dto.Corpus;

public final class UsageReferenceFormatter {
    private static final List<String> HIDDEN_LABELS = List.of(
            "id", "doc", "sadaļa", "kategorija", "datnes nosaukums", "faila nosaukums");

    private UsageReferenceFormatter() {
    }

    public record Source(String label, String url) {
    }

    public static Source format(Corpus corpus, String reference, String fallbackUrl) {
        List<String> details = new ArrayList<>();
        String sourceUrl = corpus == null ? fallbackUrl : null;
        if (reference != null) {
            for (String part : reference.split("; ")) {
                int separator = isWebUrl(part) ? -1 : part.indexOf('=');
                String label = separator < 0 ? "" : part.substring(0, separator).trim().toLowerCase(Locale.ROOT);
                String value = (separator < 0 ? part : part.substring(separator + 1)).trim();
                if (value.isEmpty() || value.equals("===NONE===")
                        || HIDDEN_LABELS.contains(label) || label.endsWith(".id")) {
                    continue;
                }
                if (isWebUrl(value)) {
                    sourceUrl = value;
                } else if ((separator >= 0 || !value.matches("(?i)(doc[#_-]?\\d+|#?\\d+)"))
                        && !details.contains(value)) {
                    details.add(value);
                }
            }
        }
        String label = String.join(", ", details);
        if (label.isEmpty()) {
            if (sourceUrl != null) {
                label = URI.create(sourceUrl).getHost();
            } else if (corpus != null) {
                label = corpus.label;
            } else {
                label = "Avots";
            }
        }
        return new Source(label, sourceUrl);
    }

    private static boolean isWebUrl(String value) {
        try {
            URI uri = URI.create(value);
            return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
