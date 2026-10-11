CREATE TABLE suggestions (
    id UUID PRIMARY KEY,
    source_suggestion_id INTEGER NOT NULL,
    reviewed_term VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    tezaurs_status VARCHAR(32) NOT NULL,
    matched_entry_id INTEGER,
    corpus_status VARCHAR(32) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_review_suggestions_source_suggestion UNIQUE (source_suggestion_id),
    CONSTRAINT ck_review_suggestions_status CHECK (
        status IN (
            'NEW', 'READY_FOR_REVIEW', 'GARBAGE', 'COMPLETED', 'IN_PROGRESS',
            'INVENTED', 'ALREADY_EXISTS', 'INSUFFICIENT_DATA', 'NEEDS_EXPERT'
        )
    ),
    CONSTRAINT ck_review_suggestions_tezaurs_status CHECK (
        tezaurs_status IN (
            'NOT_CHECKED', 'FOUND', 'MEANING_FOUND', 'MEANING_NOT_FOUND', 'NOT_FOUND'
        )
    ),
    CONSTRAINT ck_review_suggestions_corpus_status CHECK (
        corpus_status IN ('NOT_CHECKED', 'FOUND', 'NOT_FOUND')
    ),
    CONSTRAINT ck_review_suggestions_tezaurs_match CHECK (
        matched_entry_id IS NULL
        OR tezaurs_status IN ('FOUND', 'MEANING_FOUND', 'MEANING_NOT_FOUND')
    )
);

CREATE TABLE meanings (
    id UUID PRIMARY KEY,
    suggestion_id UUID NOT NULL,
    supersedes_meaning_id UUID,
    origin VARCHAR(32) NOT NULL,
    gloss TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_review_meanings_id_suggestion UNIQUE (id, suggestion_id),
    CONSTRAINT fk_review_meanings_suggestion FOREIGN KEY (suggestion_id)
        REFERENCES suggestions (id) ON DELETE CASCADE,
    CONSTRAINT fk_review_meanings_supersedes FOREIGN KEY (supersedes_meaning_id, suggestion_id)
        REFERENCES meanings (id, suggestion_id),
    CONSTRAINT ck_review_meanings_not_own_predecessor CHECK (
        supersedes_meaning_id IS NULL OR supersedes_meaning_id <> id
    ),
    CONSTRAINT ck_review_meanings_origin CHECK (
        origin IN ('SUBMITTER', 'GENERATED', 'REVIEWER', 'EXISTING')
    ),
    CONSTRAINT ck_review_meanings_status CHECK (status IN ('CURRENT', 'SUPERSEDED')),
    CONSTRAINT ck_review_meanings_gloss_not_blank CHECK (btrim(gloss) <> '')
);

CREATE INDEX ix_review_meanings_suggestion_created
    ON meanings (suggestion_id, created_at, id);

CREATE UNIQUE INDEX uq_review_meanings_current_per_suggestion
    ON meanings (suggestion_id)
    WHERE status = 'CURRENT';

CREATE TABLE corpus_examples (
    id UUID PRIMARY KEY,
    suggestion_id UUID NOT NULL REFERENCES suggestions (id) ON DELETE CASCADE,
    url TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_review_corpus_examples_url UNIQUE (suggestion_id, url)
);

CREATE INDEX ix_review_corpus_examples_suggestion_created
    ON corpus_examples (suggestion_id, created_at, id);
