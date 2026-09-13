CREATE TABLE suggestion (
    id UUID PRIMARY KEY,
    submitted_term VARCHAR(255) NOT NULL,
    reviewed_term VARCHAR(255),
    submitted_definition TEXT NOT NULL,
    usage_example TEXT,
    notes TEXT,
    submitter_name VARCHAR(255),
    submitter_email VARCHAR(320),
    status VARCHAR(32) NOT NULL,
    tezaurs_status VARCHAR(32) NOT NULL,
    matched_entry_id BIGINT,
    corpus_status VARCHAR(32) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_suggestion_status CHECK (
        status IN ('NEW', 'IN_PROGRESS', 'POSTPONED', 'NEEDS_EXPERT', 'COMPLETED', 'GARBAGE')
    ),
    CONSTRAINT ck_suggestion_tezaurs_status CHECK (
        tezaurs_status IN ('NOT_CHECKED', 'FOUND', 'NOT_FOUND')
    ),
    CONSTRAINT ck_suggestion_corpus_status CHECK (
        corpus_status IN ('NOT_CHECKED', 'FOUND', 'NOT_FOUND')
    ),
    CONSTRAINT ck_suggestion_tezaurs_match CHECK (
        (tezaurs_status = 'FOUND' AND matched_entry_id IS NOT NULL)
        OR (tezaurs_status <> 'FOUND' AND matched_entry_id IS NULL)
    )
);

CREATE INDEX ix_suggestion_status_created ON suggestion(status, created_at, id);
