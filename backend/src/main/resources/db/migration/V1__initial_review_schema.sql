CREATE TABLE cases (
    id UUID PRIMARY KEY,
    reviewed_term VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    tezaurs_status VARCHAR(32) NOT NULL,
    matched_entry_id INTEGER,
    usage_decision VARCHAR(32) NOT NULL DEFAULT 'NOT_CHECKED'
        CHECK (usage_decision IN ('NOT_CHECKED', 'USED', 'NOT_CONFIRMED', 'INVENTED')),
    usage_note TEXT,
    usage_decided_by VARCHAR(255),
    usage_decided_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_review_cases_status CHECK (
        status IN (
            'NEW', 'READY_FOR_REVIEW', 'GARBAGE', 'COMPLETED', 'IN_PROGRESS',
            'INVENTED', 'ALREADY_EXISTS', 'INSUFFICIENT_DATA', 'NEEDS_EXPERT'
        )
    ),
    CONSTRAINT ck_review_cases_tezaurs_status CHECK (
        tezaurs_status IN (
            'NOT_CHECKED', 'FOUND', 'MEANING_ALREADY_EXISTS', 'MEANING_NEEDS_EDITING', 'MEANING_NOT_FOUND', 'NOT_FOUND'
        )
    ),
    CONSTRAINT ck_review_cases_tezaurs_match CHECK (
        (tezaurs_status IN ('NOT_CHECKED', 'NOT_FOUND') AND matched_entry_id IS NULL)
        OR (tezaurs_status IN ('FOUND', 'MEANING_ALREADY_EXISTS', 'MEANING_NEEDS_EDITING', 'MEANING_NOT_FOUND')
            AND matched_entry_id IS NOT NULL)
    )
);

CREATE TABLE case_suggestions (
    source_suggestion_id INTEGER PRIMARY KEY,
    case_id UUID NOT NULL REFERENCES cases (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_review_case_suggestions_case
    ON case_suggestions (case_id);

CREATE TABLE entries (
    id UUID PRIMARY KEY,
    case_id UUID NOT NULL UNIQUE REFERENCES cases (id) ON DELETE CASCADE,
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
    case_id UUID NOT NULL REFERENCES cases (id) ON DELETE CASCADE,
    target_entry_id INTEGER,
    source_sense_id INTEGER,
    parent_draft_sense_id UUID REFERENCES senses (id) ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED,
    parent_tezaurs_sense_id INTEGER,
    gloss TEXT NOT NULL,
    order_no INTEGER NOT NULL,
    flags JSONB NOT NULL DEFAULT '{}'::jsonb,
    structural_restrictions JSONB,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_review_senses_gloss_not_blank CHECK (source_sense_id IS NOT NULL OR btrim(gloss) <> ''),
    CONSTRAINT ck_review_senses_one_parent CHECK (
        parent_draft_sense_id IS NULL OR parent_tezaurs_sense_id IS NULL
    )
);

CREATE INDEX ix_review_senses_case
    ON senses (case_id, order_no);

CREATE UNIQUE INDEX ix_review_senses_source
    ON senses (case_id, target_entry_id, source_sense_id);

CREATE TABLE usage_evidence (
    id UUID PRIMARY KEY,
    case_id UUID NOT NULL REFERENCES cases (id) ON DELETE CASCADE,
    source VARCHAR(16) NOT NULL CHECK (source IN ('CORPUS', 'EXTERNAL')),
    corpus VARCHAR(32),
    token_number BIGINT CHECK (token_number >= 0),
    search_mode VARCHAR(16),
    search_query VARCHAR(255) NOT NULL,
    citation TEXT NOT NULL,
    url TEXT NOT NULL,
    reference TEXT,
    note TEXT,
    created_by VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_review_usage_evidence_case ON usage_evidence (case_id, created_at);

CREATE UNIQUE INDEX ix_review_usage_evidence_occurrence
    ON usage_evidence (case_id, corpus, token_number);
