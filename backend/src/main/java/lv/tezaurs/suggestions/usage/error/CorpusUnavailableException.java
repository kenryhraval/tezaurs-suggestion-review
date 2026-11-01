package lv.tezaurs.suggestions.usage.error;

public class CorpusUnavailableException extends RuntimeException {
    public CorpusUnavailableException(Throwable cause) {
        super("Korpusa meklēšana pašlaik nav pieejama. Mēģiniet vēlreiz vai atveriet korpusa vietni.", cause);
    }
}
