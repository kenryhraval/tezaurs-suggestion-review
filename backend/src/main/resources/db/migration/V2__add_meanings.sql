CREATE TABLE meaning (
    id UUID PRIMARY KEY,
    suggestion_id UUID NOT NULL,
    parent_meaning_id UUID,
    supersedes_meaning_id UUID,
    origin VARCHAR(32) NOT NULL,
    gloss TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_meaning_id_suggestion UNIQUE (id, suggestion_id),
    CONSTRAINT fk_meaning_suggestion FOREIGN KEY (suggestion_id)
        REFERENCES suggestion (id) ON DELETE CASCADE,
    CONSTRAINT fk_meaning_parent FOREIGN KEY (parent_meaning_id, suggestion_id)
        REFERENCES meaning (id, suggestion_id),
    CONSTRAINT fk_meaning_supersedes FOREIGN KEY (supersedes_meaning_id, suggestion_id)
        REFERENCES meaning (id, suggestion_id),
    CONSTRAINT ck_meaning_not_own_parent CHECK (parent_meaning_id IS NULL OR parent_meaning_id <> id),
    CONSTRAINT ck_meaning_not_own_predecessor CHECK (supersedes_meaning_id IS NULL OR supersedes_meaning_id <> id),
    CONSTRAINT ck_meaning_origin CHECK (origin IN ('SUBMITTER', 'GENERATED', 'REVIEWER', 'EXISTING')),
    CONSTRAINT ck_meaning_status CHECK (status IN ('PROPOSED', 'APPROVED', 'REJECTED', 'SUPERSEDED')),
    CONSTRAINT ck_meaning_gloss_not_blank CHECK (btrim(gloss) <> '')
);

CREATE INDEX ix_meaning_suggestion_created ON meaning (suggestion_id, created_at, id);

INSERT INTO meaning (
    id, suggestion_id, parent_meaning_id, supersedes_meaning_id,
    origin, gloss, status, created_at, updated_at
)
SELECT gen_random_uuid(), id, NULL, NULL, 'SUBMITTER', submitted_definition, 'PROPOSED', created_at, updated_at
FROM suggestion;

ALTER TABLE suggestion
    DROP COLUMN submitted_definition,
    DROP COLUMN version;
