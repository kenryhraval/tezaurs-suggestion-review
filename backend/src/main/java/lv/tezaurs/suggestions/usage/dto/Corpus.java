package lv.tezaurs.suggestions.usage.dto;

// Corpora advertised by https://korpuss.lv/search
public enum Corpus {
    LVK2022("LVK2022", "LVK2022"),
    TIMEKLIS("timeklis", "Tīmeklis2007"),
    LVK2018("LVK2018", "LVK2018"),
    UDLV_LVTB("udlv_lvtb", "UDLV-LVTB"),
    LRK2013("LRK2013", "LRK2013"),
    LILA_LV("LILA_LV", "LiLa"),
    MULA("mula", "MuLa2012", "nosketch.korpuss.lv", "lc"),
    MULA2022("MuLa2022", "MuLa2022", "nosketch.korpuss.lv", "lc"),
    LAVA("lava", "LaVA"),
    VIDUSSKOLU_DIKTATI("vidusskolu_diktati", "Pārspriedumi"),
    SAEIMA("saeima", "Saeima"),
    SENIE_UNICODE("senie_unicode", "Senie", "nosketch.korpuss.lv", "lc,translit_lc"),
    RAINIS("rainis", "Rainis"),
    EMUARI("emuari", "Emuāri"),
    BAROMETRS("barometrs", "Barometrs"),
    SUBTITRI("subtitri", "Subtitri"),
    COMMONCRAWL("CommonCrawl", "Tīmeklis2020"),
    LAT_SEN_ROM("lat_sen_rom", "LatSenRom", "korpuss.lnb.lv"),
    PANDEMIJAS_DIENASGRAMATAS("pandemijas_dienasgramatas", "PanDi", "nosketch.lnb.lv"),
    SIEVIESU_ISPROZA("sieviesu_isproza", "ĪsprozaS", "korpuss.lnb.lv"),
    DISERTACIJAS("disertacijas", "Disertācijas"),
    LIKUMI("likumi", "Likumi"),
    VIKIPEDIJA("Vikipedija", "Vikipēdija"),
    LVMED("LVMED", "LVMED"),
    ZINAS("zinas", "Ziņas"),
    LATVIESU_AVIZES_20220928("latviesu_avizes_20220928", "LAvīzes", "nosketch.lnb.lv"),
    KAROGS("karogs", "Karogs", "korpuss.lnb.lv"),
    LITERATURA_MAKSLA("literatura_maksla", "LitMāksla", "korpuss.lnb.lv"),
    COMMON_VOICE_LV("common_voice_lv", "BalsuTalka"),
    COMMON_VOICE_LTG("common_voice_ltg", "BolsuTolka", "nosketch.korpuss.lv", "lc"),
    LATE_SARUNAS("late_sarunas", "LATE-sarunas"),
    LATE_MEDIJI("late_mediji", "LATE-mediji"),
    CINA("cina", "Cīņa", "korpuss.lnb.lv"),
    PADOMJU_JAUNATNE("padomju_jaunatne", "Jaunatne", "nosketch.lnb.lv"),
    MULAR("mular", "MuLaR", "nosketch.korpuss.lv", "word"),
    SATORI_PUNCTUM("satori-punctum", "Satori-Punctum", "nosketch.korpuss.lv", "word"),
    FONLATE("fonlate", "fonLATE"),
    AUTOBIO("autobio", "AutoBio"),
    LPT_TEIKAS("lpt_teikas", "LPT-teikas"),
    LATVJU_DAINAS("latvju_dainas", "Latvju-dainas"),
    LATVJU_DAINAS_LTG("latvju_dainas_ltg", "Latvju-dainas-ltg", "nosketch.korpuss.lv", "lc"),
    TVITEDIENS("tvitediens", "Tvitēdiens");

    public final String corpusId;
    public final String label;
    public final String apiUrl;
    public final String references;
    public final String[] searchAttributes;

    Corpus(String corpusId, String label) {
        this(corpusId, label, "nosketch.korpuss.lv");
    }

    Corpus(String corpusId, String label, String host) {
        this(corpusId, label, host, "lc,lemma_lc");
    }

    Corpus(String corpusId, String label, String host, String searchAttributes) {
        this.corpusId = corpusId;
        this.label = label;
        this.apiUrl = "https://" + host + "/bonito/run.cgi";
        this.searchAttributes = searchAttributes.split(",");
        this.references = switch (corpusId) {
            case "LVK2022", "LVK2018" -> "doc.reference,doc.id,doc.section";
            case "timeklis" -> "doc.file";
            case "mula" -> "doc.author,doc.source,doc.published,doc.title";
            case "MuLa2022" -> "doc.author,doc.year,doc.issue,doc.title";
            case "barometrs" -> "doc.article_title,doc.date";
            case "disertacijas" -> "doc.title,doc.year,doc.url";
            case "emuari" -> "doc.author,doc.title,doc.source,doc.published";
            case "likumi" -> "doc.title,doc.date,doc.url";
            case "rainis" -> "doc.source,doc.title";
            case "saeima" -> "doc.date,doc.name";
            case "subtitri" -> "doc.title,doc.date";
            case "CommonCrawl" -> "doc.source,doc.filename";
            case "Vikipedija" -> "doc.title,doc.url";
            case "zinas" -> "doc.title,doc.publish_time,doc.url";
            case "pandemijas_dienasgramatas" -> "doc.creator,doc.firstDate,doc.lastDate";
            case "latviesu_avizes_20220928" -> "doc.title,doc.dateIssued,doc.issueNumber,doc.page";
            case "padomju_jaunatne", "cina", "karogs", "literatura_maksla" ->
                    "doc.author,doc.title,doc.dateIssued,doc.issueNumber,doc.page";
            case "sieviesu_isproza" -> "doc.creator,doc.title,doc.dateIssued";
            case "lat_sen_rom" -> "doc.creator,doc.workTitle,doc.firstEdition,doc.publicationDate";
            case "satori-punctum" -> "doc.ref,doc.url,doc.year";
            case "autobio" -> "doc.title,doc.year_from,doc.year_to";
            case "lpt_teikas" -> "doc.title,doc.section";
            case "tvitediens" -> "doc.date,doc.url";
            default -> "doc";
        };
    }
}
