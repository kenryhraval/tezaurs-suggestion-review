package lv.tezaurs.tip.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import lv.tezaurs.tip.corpus.dto.Corpus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class CorpusCitationTest {
    record Citation(Corpus corpus, String reference, String label) {
    }

    static Stream<Citation> citations() {
        return Stream.of(
                new Citation(Corpus.MULA,
                        "Nosaukums=Laiks &amp; darbi; Autors=Jānis; Avots=Latvijas Radio; Publicēts=2010",
                        "\"Laiks & darbi\", Jānis, Latvijas Radio, 2010"),
                new Citation(Corpus.MULA2022,
                        "doc.title=Raksts; doc.author=Anna; doc.issue=Izdevums; doc.year=2022",
                        "\"Raksts\", Anna, Izdevums, 2022"),
                new Citation(Corpus.BAROMETRS, "doc.year=2020; Raksta nosaukums=Raksts",
                        "Barometrs: interneta agresivitātes indekss, 2020"),
                new Citation(Corpus.DISERTACIJAS, "Nosaukums=Pētījums; Gads=2023; Nozare=Medicīna",
                        "Disertācijas, Pētījums, 2023"),
                new Citation(Corpus.EMUARI, "Virsraksts=Dienas piezīmes; Avots=https://example.com/blog",
                        "Dienas piezīmes"),
                new Citation(Corpus.LIKUMI, "Izdevējs=Saeima; Nosaukums=Likums; Publicēšanas datums=2024-01-02",
                        "Saeima, Likums, 2024-01-02"),
                new Citation(Corpus.RAINIS, "Nosaukums=Kopoti raksti|Dzeja|Gals un sākums|", "Rainis, \"Gals un sākums\""),
                new Citation(Corpus.SAEIMA, "doc.name=Jānis Bērziņš; doc.category=14. Saeima|Frakcija; doc.date=2024|novembris",
                        "Jānis Bērziņš. 14. Saeima, Frakcija. 2024. novembris"),
                new Citation(Corpus.SAEIMA, "Vārds=Jānis Bērziņš; Datums=2024-11-01",
                        "Jānis Bērziņš. 2024-11-01"),
                new Citation(Corpus.SUBTITRI, "Nosaukums=Filma; Datums=2020", "Filma, 2020"),
                new Citation(Corpus.VIKIPEDIJA, "Nosaukums=Suns; Avots=https://lv.wikipedia.org/wiki/Suns", "Vikipēdija, \"Suns\""),
                new Citation(Corpus.ZINAS, "doc.publish_time=2024-01-02; doc.title=Raksts", "\"Raksts\", 2024-01-02"),
                new Citation(Corpus.KAROGS, "Autors=Anna; Nosaukums=Stāsts; Izdošanas gads=1987; Žurnāla numurs=12",
                        "Anna, \"Stāsts\" – Karogs, 1987, Nr. 12"),
                new Citation(Corpus.LITERATURA_MAKSLA, "Nosaukums=Raksts; doc.dateIssued=1987; doc.issueNumber=3",
                        "\"Raksts\" – Literatūra un Māksla, 1987, Nr. 3"),
                new Citation(Corpus.CINA, "Autors====NONE===; Nosaukums=Raksts", "\"Raksts\" – Cīņa"),
                new Citation(Corpus.PADOMJU_JAUNATNE, "doc.issueNumber=5", "Padomju Jaunatne, Nr. 5"),
                new Citation(Corpus.SIEVIESU_ISPROZA, "doc.creator=Anna; doc.title=Stāsts; doc.dateIssued=1930",
                        "Anna, \"Stāsts\", 1930"),
                new Citation(Corpus.LAT_SEN_ROM,
                        "Darba autors (dzīves gadi)=Autors (1900–1950); Darba nosaukums=Romāns; Darba pirmizdevuma izdošanas gads=1930",
                        "Autors (1900–1950), \"Romāns\", 1930"),
                new Citation(Corpus.PANDEMIJAS_DIENASGRAMATAS, "doc.creator=Anna; doc.firstDate=2020-03-01; doc.lastDate=2020-04-01",
                        "Anna, 2020-03-01–2020-04-01"),
                new Citation(Corpus.PANDEMIJAS_DIENASGRAMATAS, "Sākuma datums=2020-03-01; Beigu datums=2020-03-01", "2020-03-01"),
                new Citation(Corpus.LATVIESU_AVIZES_20220928, "Nosaukums=Ziņa; Izdošanas gads=1920; Avīzes numurs=7",
                        "Latviešu avīzes, \"Ziņa\", 1920, Nr. 7"),
                new Citation(Corpus.SATORI_PUNCTUM, "Atsauce=Anna. Stāsts (2024); Publicēšanas gads=2024", "Anna. Stāsts (2024)"),
                new Citation(Corpus.AUTOBIO, "Nosaukums=Atmiņas; Rakstīšanas sākums=1900; Rakstīšanas beigas=1910", "Atmiņas (1900–1910)"),
                new Citation(Corpus.AUTOBIO, "Nosaukums=Atmiņas; doc.year_from=1900", "Atmiņas (1900)"),
                new Citation(Corpus.LATVJU_DAINAS, "Nodaļa=Darba dziesmas; ID=123", "Latvju dainas, Darba dziesmas"),
                new Citation(Corpus.LATVJU_DAINAS_LTG, "doc.section=Darba dziesmas; doc.id=123", "Latvju dainas, Darba dziesmas"),
                new Citation(Corpus.LPT_TEIKAS, "Nosaukums=Teika; doc.id=123", "Latviešu pasakas un teikas, \"Teika\""),
                new Citation(Corpus.TVITEDIENS, "Gads=2020; doc.id=123", "Twitter, 2020"),
                new Citation(Corpus.TVITEDIENS, "Datums=2020-01-03; doc.id=123", "Twitter, 2020"),
                new Citation(Corpus.UDLV_LVTB, "doc.id=doc1; s.id=sent-42; p.id=p1", "LVTB teikums sent-42"),
                new Citation(Corpus.COMMON_VOICE_LTG, "ID=123", "BolsuTolka"),
                new Citation(Corpus.COMMON_VOICE_LV, "doc.year=2024", "BalsuTalka 2024"),
                new Citation(Corpus.LATE_MEDIJI, "ID=123", "LATE mediji"),
                new Citation(Corpus.LATE_SARUNAS, "Datnes nosaukums=file.txt", "LATE sarunas"),
                new Citation(Corpus.VIDUSSKOLU_DIKTATI, "Skola=Skola", "Pārspriedumi"),
                new Citation(Corpus.LVMED, "Nozare=Medicīna", "LVMED"),
                new Citation(Corpus.LVK2022, "Atsauce=Anna. Grāmata. Rīga, 2000; doc.id=123", "Anna. Grāmata. Rīga, 2000"),
                new Citation(Corpus.ZINAS, "doc.title=Pirmā daļa; otrā daļa; doc.publish_time=2024", "\"Pirmā daļa; otrā daļa\", 2024"),
                new Citation(Corpus.ZINAS, "Nosaukums=; Nosaukums=Raksts", "\"Raksts\""),
                new Citation(Corpus.ZINAS, "Nosaukums=\"Raksts\"", "\"Raksts\"")
        );
    }

    @ParameterizedTest
    @MethodSource("citations")
    void formatsCorpusSpecificCitations(Citation citation) {
        assertThat(ExampleSourceFormatter.format(citation.corpus(), citation.reference(), null).label())
                .isEqualTo(citation.label());
    }

    @ParameterizedTest
    @EnumSource(Corpus.class)
    void missingMetadataNeverProducesEmptyQuotesOrDanglingPunctuation(Corpus corpus) {
        var source = ExampleSourceFormatter.format(corpus,
                "Nosaukums====NONE===; Autors=; Gads=; Avots=; doc.id=123", "https://korpuss.lv/search");
        assertThat(source.label()).isNotBlank().doesNotContain("\"\"", "null", "undefined", "()", "===NONE===")
                .doesNotStartWith(",").doesNotStartWith("–").doesNotStartWith(".")
                .doesNotEndWith(",").doesNotEndWith("–").doesNotEndWith("(").doesNotEndWith("-");
        assertThat(source.url()).isNull();
    }

    @Test
    void keepsTheDocumentUrlInsteadOfThePublisherOrAnAuthorProfile() {
        var source = ExampleSourceFormatter.format(Corpus.ZINAS,
                "Avots=https://publisher.example; Autors=https://author.example; doc.url=https://publisher.example/article?a=1&amp;b=2; Nosaukums=Raksts", null);
        assertThat(source.label()).isEqualTo("\"Raksts\"");
        assertThat(source.url()).isEqualTo("https://publisher.example/article?a=1&b=2");
    }

    @Test
    void usesAReadableHostForWebOnlySourcesWithoutRepeatingTheUrlInTheLabel() {
        var source = ExampleSourceFormatter.format(Corpus.COMMONCRAWL, "Avota URL=https://example.com/article?id=2", null);
        assertThat(source.label()).isEqualTo("example.com");
        assertThat(source.url()).isEqualTo("https://example.com/article?id=2");
        assertThat(ExampleSourceFormatter.format(Corpus.TIMEKLIS,
                "Datnes nosaukums=https://example.com/article", null).label()).isEqualTo("example.com");
    }

    @ParameterizedTest
    @ValueSource(strings = {"not a url", "javascript:alert(1)", "ftp://example.com/file", ""})
    void ignoresInvalidOrNonWebFallbackUrls(String fallbackUrl) {
        var source = ExampleSourceFormatter.format(null, null, fallbackUrl);
        assertThat(source.label()).isEqualTo("Avots");
        assertThat(source.url()).isNull();
    }
}
