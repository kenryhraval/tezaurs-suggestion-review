CREATE TABLE suggestions (
    id UUID PRIMARY KEY,
    reviewed_term VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    tezaurs_status VARCHAR(32) NOT NULL,
    matched_entry_id INTEGER,
    entry_draft_ready BOOLEAN NOT NULL DEFAULT FALSE,
    sense_draft_ready BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
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
    CONSTRAINT ck_review_suggestions_tezaurs_match CHECK (
        matched_entry_id IS NULL
        OR tezaurs_status IN ('FOUND', 'MEANING_FOUND', 'MEANING_NOT_FOUND')
    )
);

CREATE TABLE suggestion_sources (
    source_suggestion_id INTEGER PRIMARY KEY,
    suggestion_id UUID NOT NULL REFERENCES suggestions (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_review_suggestion_sources_suggestion
    ON suggestion_sources (suggestion_id);

CREATE TABLE entries (
    id UUID PRIMARY KEY,
    suggestion_id UUID NOT NULL UNIQUE REFERENCES suggestions (id) ON DELETE CASCADE,
    heading VARCHAR(255) NOT NULL,
    homonym_no INTEGER NOT NULL,
    type_id INTEGER NOT NULL,
    etymology TEXT,
    normative_comment TEXT,
    scientific_name TEXT,
    source TEXT,
    notes TEXT,
    flags JSONB NOT NULL DEFAULT '{}'::jsonb,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_review_entries_heading_not_blank CHECK (btrim(heading) <> ''),
    CONSTRAINT ck_review_entries_homonym_no CHECK (homonym_no >= 1)
);

CREATE TABLE lexemes (
    id UUID PRIMARY KEY,
    entry_id UUID NOT NULL REFERENCES entries (id) ON DELETE CASCADE,
    lemma VARCHAR(255) NOT NULL,
    primary_lexeme BOOLEAN NOT NULL,
    role_id INTEGER NOT NULL,
    paradigm_id INTEGER,
    stem1 TEXT,
    stem2 TEXT,
    stem3 TEXT,
    pronunciations JSONB NOT NULL DEFAULT '[]'::jsonb,
    flags JSONB NOT NULL DEFAULT '{}'::jsonb,
    structural_restrictions JSONB,
    order_no INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_review_lexemes_lemma_not_blank CHECK (btrim(lemma) <> '')
);

CREATE INDEX ix_review_lexemes_entry
    ON lexemes (entry_id);

CREATE TABLE senses (
    id UUID PRIMARY KEY,
    suggestion_id UUID NOT NULL REFERENCES suggestions (id) ON DELETE CASCADE,
    parent_draft_sense_id UUID REFERENCES senses (id) ON DELETE CASCADE,
    parent_tezaurs_sense_id INTEGER,
    gloss TEXT NOT NULL,
    order_no INTEGER NOT NULL,
    flags JSONB NOT NULL DEFAULT '{}'::jsonb,
    structural_restrictions JSONB,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_review_senses_gloss_not_blank CHECK (btrim(gloss) <> ''),
    CONSTRAINT ck_review_senses_one_parent CHECK (
        parent_draft_sense_id IS NULL OR parent_tezaurs_sense_id IS NULL
    )
);

CREATE INDEX ix_review_senses_suggestion
    ON senses (suggestion_id, order_no);
