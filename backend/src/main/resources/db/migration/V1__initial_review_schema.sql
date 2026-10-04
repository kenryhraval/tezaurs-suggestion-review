CREATE TABLE suggestion (
    id UUID PRIMARY KEY,
    source_suggestion_id INTEGER NOT NULL,
    reviewed_term VARCHAR(255),
    tezaurs_status VARCHAR(32) NOT NULL,
    matched_entry_id BIGINT,
    corpus_status VARCHAR(32) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_review_suggestion_source UNIQUE (source_suggestion_id),
    CONSTRAINT ck_review_suggestion_tezaurs_status CHECK (
        tezaurs_status IN (
            'NOT_CHECKED', 'FOUND', 'MEANING_FOUND', 'MEANING_NOT_FOUND', 'NOT_FOUND'
        )
    ),
    CONSTRAINT ck_review_suggestion_corpus_status CHECK (
        corpus_status IN ('NOT_CHECKED', 'FOUND', 'NOT_FOUND')
    ),
    CONSTRAINT ck_review_suggestion_tezaurs_match CHECK (
        matched_entry_id IS NULL
        OR tezaurs_status IN ('FOUND', 'MEANING_FOUND', 'MEANING_NOT_FOUND')
    )
);

CREATE TABLE meaning (
    id UUID PRIMARY KEY,
    suggestion_id UUID NOT NULL,
    supersedes_meaning_id UUID,
    origin VARCHAR(32) NOT NULL,
    gloss TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_review_meaning_id_suggestion UNIQUE (id, suggestion_id),
    CONSTRAINT fk_review_meaning_suggestion FOREIGN KEY (suggestion_id)
        REFERENCES suggestion (id) ON DELETE CASCADE,
    CONSTRAINT fk_review_meaning_supersedes FOREIGN KEY (supersedes_meaning_id, suggestion_id)
        REFERENCES meaning (id, suggestion_id),
    CONSTRAINT ck_review_meaning_not_own_predecessor CHECK (
        supersedes_meaning_id IS NULL OR supersedes_meaning_id <> id
    ),
    CONSTRAINT ck_review_meaning_origin CHECK (
        origin IN ('SUBMITTER', 'GENERATED', 'REVIEWER', 'EXISTING')
    ),
    CONSTRAINT ck_review_meaning_status CHECK (status IN ('CURRENT', 'SUPERSEDED')),
    CONSTRAINT ck_review_meaning_gloss_not_blank CHECK (btrim(gloss) <> '')
);

CREATE INDEX ix_review_meaning_suggestion_created
    ON meaning (suggestion_id, created_at, id);

CREATE UNIQUE INDEX uq_review_meaning_current_per_suggestion
    ON meaning (suggestion_id)
    WHERE status = 'CURRENT';

CREATE TABLE corpus_example (
    id UUID PRIMARY KEY,
    suggestion_id UUID NOT NULL REFERENCES suggestion (id) ON DELETE CASCADE,
    url TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_review_corpus_example_url UNIQUE (suggestion_id, url)
);

CREATE INDEX ix_review_corpus_example_suggestion_created
    ON corpus_example (suggestion_id, created_at, id);
