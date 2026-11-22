package lv.tezaurs.tip.common.error;

public class SenseModelException extends RuntimeException {
    public SenseModelException(Throwable cause) {
        super("MI atbilde nav pieejama vai nav derīga. Saglabātie dati nav mainīti; mēģiniet vēlreiz.", cause);
    }
}
