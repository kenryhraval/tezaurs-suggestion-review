CREATE FUNCTION generate_case_public_id() RETURNS TEXT
LANGUAGE plpgsql AS $$
DECLARE
    alphabet CONSTANT TEXT := 'abcdefghijklmnopqrstuvwxyz0123456789';
    code_length INTEGER := 6;
    candidate TEXT;
BEGIN
    -- Keep allocation atomic across concurrent case inserts.
    PERFORM pg_advisory_xact_lock(714, 2);
    LOOP
        FOR attempt IN 1..10 LOOP
            candidate := '';
            FOR character_number IN 1..code_length LOOP
                candidate := candidate || substr(alphabet, 1 + floor(random() * length(alphabet))::INTEGER, 1);
            END LOOP;
            IF NOT EXISTS (SELECT 1 FROM review.cases WHERE public_id = candidate) THEN
                RETURN candidate;
            END IF;
        END LOOP;
        code_length := code_length + 1;
    END LOOP;
END;
$$;

CREATE TABLE cases (
    id UUID PRIMARY KEY,
    public_id TEXT NOT NULL UNIQUE DEFAULT generate_case_public_id()
        CHECK (public_id ~ '^[a-z0-9]{6,}$'),
    reviewed_term TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    last_status_changed_by VARCHAR(255) CHECK (btrim(last_status_changed_by) <> ''),
    type VARCHAR(16) NOT NULL,
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
    CONSTRAINT ck_review_cases_type CHECK (type IN ('UNKNOWN', 'ENTRY', 'SENSE')),
    CONSTRAINT ck_review_cases_type_match CHECK (
        (type IN ('UNKNOWN', 'ENTRY') AND matched_entry_id IS NULL)
        OR (type = 'SENSE' AND matched_entry_id IS NOT NULL)
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
    parent_draft_sense_id UUID REFERENCES senses (id) ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED,
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

CREATE INDEX ix_review_senses_case
    ON senses (case_id, target_entry_id, order_no);

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
