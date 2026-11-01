package lv.tezaurs.suggestions.usage.service;

import static org.assertj.core.api.Assertions.assertThat;

import lv.tezaurs.suggestions.usage.dto.Corpus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class UsageReferenceFormatterTest {
    @Test
    void formatsReadableDetailsAndKeepsTheOriginalSourceLinkSeparate() {
        var source = UsageReferenceFormatter.format(Corpus.ZINAS,
                "Nosaukums=Raksts; Datums=2024; Avots=https://example.com/article; ID=42; sadaļa=ziņas", null);
        assertThat(source.label()).isEqualTo("Raksts, 2024");
        assertThat(source.url()).isEqualTo("https://example.com/article");
    }

    @Test
    void skipsEmptyValuesTechnicalIdsAndRepeatedDetails() {
        var source = UsageReferenceFormatter.format(Corpus.LVK2022,
                "Atsauce=Grāmata; doc.id=123; Gads====NONE===; Avots=Grāmata; doc-123; 123; ", null);
        assertThat(source.label()).isEqualTo("Grāmata");
        assertThat(source.url()).isNull();
    }

    @Test
    void usesTheCorpusLabelWhenNoReadableReferenceIsAvailable() {
        var source = UsageReferenceFormatter.format(Corpus.LVK2022, "ID=doc-1", null);
        assertThat(source.label()).isEqualTo("LVK2022");
        assertThat(source.url()).isNull();
    }

    @Test
    void usesTheWebsiteHostnameForManualEvidence() {
        var source = UsageReferenceFormatter.format(null, null, "https://example.com/article");
        assertThat(source.label()).isEqualTo("example.com");
        assertThat(source.url()).isEqualTo("https://example.com/article");
    }

    @Test
    void acceptsUrlsContainingEqualsSignsAndRejectsNonWebLinks() {
        var source = UsageReferenceFormatter.format(Corpus.ZINAS,
                "https://example.com/article?id=2; Avots=javascript:alert(1)", null);
        assertThat(source.url()).isEqualTo("https://example.com/article?id=2");
    }

    @Test
    void handlesMissingReferences() {
        assertThat(UsageReferenceFormatter.format(Corpus.RAINIS, null, null).label()).isEqualTo("Rainis");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"ID=42; doc.id=123"})
    void handlesMissingCorpusAndUrl(String reference) {
        var source = UsageReferenceFormatter.format(null, reference, null);

        assertThat(source.label()).isEqualTo("Avots");
        assertThat(source.url()).isNull();
    }
}
