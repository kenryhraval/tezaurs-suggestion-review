package lv.tezaurs.tip.common.util;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lv.tezaurs.tip.corpus.dto.Corpus;
import org.springframework.web.util.HtmlUtils;

public final class ExampleSourceFormatter {
    private static final List<String> HIDDEN_FIELDS = List.of(
            "id", "doc", "sadaļa", "section", "kategorija", "category", "datnes nosaukums",
            "faila nosaukums", "file", "filename", "teikuma id", "sentence_id");
    private static final List<String> URL_FIELDS = List.of(
            "url", "avota url", "publicationlink", "source_url", "saite", "link");

    private ExampleSourceFormatter() {
    }

    public record Source(String label, String url) {
    }

    public static Source format(Corpus corpus, String reference, String fallbackUrl) {
        ReferenceFields fields = ReferenceFields.parse(reference);
        String sourceUrl = fields.sourceUrl();
        if (sourceUrl == null && corpus == null && isWebUrl(fallbackUrl)) {
            sourceUrl = fallbackUrl;
        }
        String label = corpus == null ? "" : corpusCitation(corpus, fields);
        if (label.isBlank()) {
            label = fields.readableDetails();
        }
        if (label.isBlank()) {
            if (sourceUrl != null) {
                label = URI.create(sourceUrl).getHost();
            } else {
                label = corpus == null ? "Avots" : corpus.label;
            }
        }
        return new Source(label, sourceUrl);
    }

    private static String corpusCitation(Corpus corpus, ReferenceFields fields) {
        return switch (corpus) {
            case MULA -> joinNonEmpty(", ", quoted(fields.title()), fields.author(), fields.sourceName(),
                    fields.get("Publicēts", "published"));
            case MULA2022 -> joinNonEmpty(", ", quoted(fields.title()), fields.author(),
                    fields.get("Izdevums", "issue"), fields.year());
            case BAROMETRS -> joinNonEmpty(", ", "Barometrs: interneta agresivitātes indekss", fields.year());
            case DISERTACIJAS -> joinNonEmpty(", ", "Disertācijas", fields.title(), fields.year());
            case EMUARI -> joinNonEmpty(", ", fields.title(), fields.sourceName());
            case LIKUMI -> joinNonEmpty(", ", fields.get("Izdevējs", "legislator"), fields.title(),
                    fields.get("Publicēšanas datums", "Datums", "date"));
            case RAINIS -> joinNonEmpty(", ", "Rainis", quoted(lastTitleSegment(fields.title())));
            case SAEIMA -> parliamentaryCitation(fields);
            case SUBTITRI -> joinNonEmpty(", ", fields.title(), fields.date());
            case VIKIPEDIJA -> joinNonEmpty(", ", "Vikipēdija", quoted(fields.title()));
            case ZINAS -> joinNonEmpty(", ", quoted(fields.title()), fields.date());
            case KAROGS, LITERATURA_MAKSLA, CINA, PADOMJU_JAUNATNE -> newspaperCitation(corpus, fields);
            case SIEVIESU_ISPROZA -> joinNonEmpty(", ", fields.author(), quoted(fields.title()), fields.year());
            case LAT_SEN_ROM -> joinNonEmpty(", ", fields.author(), quoted(fields.title()),
                    fields.get("Darba pirmizdevuma izdošanas gads", "firstEdition", "Izdošanas gads", "year"));
            case PANDEMIJAS_DIENASGRAMATAS -> joinNonEmpty(", ", fields.author(),
                    dateRange(fields.get("Sākuma datums", "firstDate"), fields.get("Beigu datums", "lastDate")));
            case LATVIESU_AVIZES_20220928 -> joinNonEmpty(", ", "Latviešu avīzes", quoted(fields.title()),
                    issueDate(fields));
            case SATORI_PUNCTUM -> fields.reference();
            case AUTOBIO -> autobiographicalCitation(fields);
            case LATVJU_DAINAS, LATVJU_DAINAS_LTG -> joinNonEmpty(", ", "Latvju dainas",
                    fields.get("Nodaļa", "section"));
            case LPT_TEIKAS -> joinNonEmpty(", ", "Latviešu pasakas un teikas", quoted(fields.title()));
            case TVITEDIENS -> joinNonEmpty(", ", "Twitter", fields.year());
            case UDLV_LVTB -> prefixed("LVTB teikums", fields.get("Teikuma ID", "s.id", "sentence_id"));
            case COMMON_VOICE_LV, COMMON_VOICE_LTG -> joinNonEmpty(" ", corpus.label, fields.year());
            case LATE_MEDIJI -> "LATE mediji";
            case LATE_SARUNAS -> "LATE sarunas";
            case VIDUSSKOLU_DIKTATI -> "Pārspriedumi";
            case LVMED -> "LVMED";
            default -> fields.reference();
        };
    }

    private static String newspaperCitation(Corpus corpus, ReferenceFields fields) {
        String publication = switch (corpus) {
            case LITERATURA_MAKSLA -> "Literatūra un Māksla";
            case PADOMJU_JAUNATNE -> "Padomju Jaunatne";
            default -> corpus.label;
        };
        String article = joinNonEmpty(", ", fields.author(), quoted(fields.title()));
        String issue = joinNonEmpty(", ", publication, issueDate(fields));
        return joinNonEmpty(" – ", article, issue);
    }

    private static String issueDate(ReferenceFields fields) {
        return joinNonEmpty(", ", fields.year(),
                prefixed("Nr.", fields.get("Žurnāla numurs", "Avīzes numurs", "Izdevuma numurs", "Numurs", "issueNumber")));
    }

    private static String parliamentaryCitation(ReferenceFields fields) {
        String category = joinNonEmpty(", ", fields.get("Kategorija", "category").split("\\|"));
        String date = joinNonEmpty(". ", fields.date().split("\\|"));
        return joinNonEmpty(". ", fields.get("Vārds", "name"), category, date);
    }

    private static String autobiographicalCitation(ReferenceFields fields) {
        String period = dateRange(fields.get("Rakstīšanas sākums", "year_from"),
                fields.get("Rakstīšanas beigas", "year_to"));
        if (period.isBlank()) {
            return fields.title();
        }
        return joinNonEmpty(" ", fields.title(), "(" + period + ")");
    }

    private static String lastTitleSegment(String title) {
        String[] segments = title.split("\\|", -1);
        for (int index = segments.length - 1; index >= 0; index--) {
            if (!segments[index].isBlank()) {
                return segments[index].trim();
            }
        }
        return "";
    }

    private static String dateRange(String start, String end) {
        if (start.equals(end)) {
            return start;
        }
        return joinNonEmpty("–", start, end);
    }

    private static String quoted(String value) {
        if (value.isBlank()) {
            return "";
        }
        if (value.startsWith("\"") && value.endsWith("\"")) {
            return value;
        }
        return "\"" + value + "\"";
    }

    private static String prefixed(String prefix, String value) {
        return value.isBlank() ? "" : prefix + " " + value;
    }

    private static String joinNonEmpty(String separator, String... parts) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (String part : parts) {
            if (part != null && !part.isBlank()) {
                values.add(part.trim());
            }
        }
        return String.join(separator, values);
    }

    private static boolean isWebUrl(String value) {
        if (value == null) {
            return false;
        }
        try {
            URI uri = URI.create(value);
            return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean isTechnicalField(String field) {
        return HIDDEN_FIELDS.contains(field) || field.endsWith(".id");
    }

    private static boolean isBareDocumentId(String value) {
        return value.matches("(?i)(doc[#_-]?\\d+|#?\\d+)");
    }

    private static String fieldName(String name) {
        String normalized = name.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("doc.") ? normalized.substring(4) : normalized;
    }

    private static String cleanValue(String value) {
        String clean = value.trim().replaceAll("[\\s\\u00a0]+", " ");
        return clean.equals("===NONE===") ? "" : clean;
    }

    private static final class ReferenceFields {
        private final Map<String, String> values = new LinkedHashMap<>();
        private final List<String> unlabelledValues = new ArrayList<>();

        static ReferenceFields parse(String reference) {
            ReferenceFields fields = new ReferenceFields();
            if (reference == null || reference.isBlank()) {
                return fields;
            }
            String previousField = null;
            for (String part : HtmlUtils.htmlUnescape(reference).split("; ")) {
                String value = cleanValue(part);
                if (value.isBlank() || isBareDocumentId(value)) {
                    continue;
                }
                if (isWebUrl(value)) {
                    fields.unlabelledValues.add(value);
                    previousField = null;
                    continue;
                }
                int separator = value.indexOf('=');
                if (separator > 0) {
                    previousField = fieldName(value.substring(0, separator));
                    String fieldValue = cleanValue(value.substring(separator + 1));
                    String previousValue = fields.values.get(previousField);
                    if (previousValue == null || previousValue.isBlank()) {
                        fields.values.put(previousField, fieldValue);
                    }
                } else if (previousField != null && !isTechnicalField(previousField)
                        && !fields.values.get(previousField).isBlank() && !isWebUrl(fields.values.get(previousField))) {
                    fields.values.put(previousField, fields.values.get(previousField) + "; " + value);
                } else {
                    fields.unlabelledValues.add(value);
                }
            }
            return fields;
        }

        String get(String... names) {
            for (String name : names) {
                String value = values.get(fieldName(name));
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
            return "";
        }

        String author() {
            return get("Autors", "Darba autors (dzīves gadi)", "author", "creator");
        }

        String title() {
            return get("Nosaukums", "Teksta nosaukums", "Virsraksts", "Darba nosaukums", "Raksta nosaukums",
                    "title", "workTitle", "article_title");
        }

        String year() {
            String year = get("Gads", "Izdošanas gads", "year", "dateIssued", "Publikācijas gads", "publicationYear");
            if (!year.isBlank()) {
                return year;
            }
            String date = date();
            if (date.length() >= 4 && date.substring(0, 4).matches("\\d{4}")) {
                return date.substring(0, 4);
            }
            return "";
        }

        String date() {
            return get("Datums", "Publicēšanas datums", "date", "publish_time", "published");
        }

        String sourceName() {
            String source = get("Avots", "source");
            return isWebUrl(source) ? "" : source;
        }

        String reference() {
            String reference = get("Atsauce", "reference", "ref");
            return isWebUrl(reference) ? "" : reference;
        }

        String sourceUrl() {
            for (String field : List.of("url", "Avota URL", "publicationLink", "source_url", "Saite", "link",
                    "Avots", "source", "Datnes nosaukums", "file", "filename", "Atsauce", "reference", "ref")) {
                String value = get(field);
                if (isWebUrl(value)) {
                    return value;
                }
            }
            for (String value : unlabelledValues) {
                if (isWebUrl(value)) {
                    return value;
                }
            }
            return null;
        }

        String readableDetails() {
            LinkedHashSet<String> details = new LinkedHashSet<>();
            for (var field : values.entrySet()) {
                if (!isTechnicalField(field.getKey()) && !URL_FIELDS.contains(field.getKey())
                        && !field.getValue().isBlank() && !isWebUrl(field.getValue())) {
                    details.add(field.getValue());
                }
            }
            for (String value : unlabelledValues) {
                if (!isWebUrl(value) && !isBareDocumentId(value)) {
                    details.add(value);
                }
            }
            return String.join(", ", details);
        }
    }
}
