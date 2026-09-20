ALTER TABLE suggestion
    DROP CONSTRAINT ck_suggestion_tezaurs_match,
    DROP CONSTRAINT ck_suggestion_tezaurs_status;

ALTER TABLE suggestion
    ADD CONSTRAINT ck_suggestion_tezaurs_status CHECK (
        tezaurs_status IN (
            'NOT_CHECKED', 'FOUND', 'MEANING_FOUND', 'MEANING_NOT_FOUND', 'NOT_FOUND'
        )
    ),
    ADD CONSTRAINT ck_suggestion_tezaurs_match CHECK (
        matched_entry_id IS NULL
        OR tezaurs_status IN ('FOUND', 'MEANING_FOUND', 'MEANING_NOT_FOUND')
    );

UPDATE suggestion
SET corpus_status = 'NOT_FOUND'
WHERE tezaurs_status IN ('MEANING_NOT_FOUND', 'NOT_FOUND')
  AND corpus_status = 'NOT_CHECKED';

WITH RECURSIVE submitted_chain AS (
    SELECT id
    FROM meaning
    WHERE origin = 'SUBMITTER'

    UNION ALL

    SELECT revision.id
    FROM meaning revision
    JOIN submitted_chain previous ON revision.supersedes_meaning_id = previous.id
)
DELETE FROM meaning
WHERE id NOT IN (SELECT id FROM submitted_chain);

ALTER TABLE meaning
    DROP CONSTRAINT fk_meaning_parent,
    DROP CONSTRAINT ck_meaning_not_own_parent,
    DROP COLUMN parent_meaning_id,
    DROP CONSTRAINT ck_meaning_status;

UPDATE meaning
SET status = 'CURRENT'
WHERE status <> 'SUPERSEDED';

ALTER TABLE meaning
    ADD CONSTRAINT ck_meaning_status CHECK (status IN ('CURRENT', 'SUPERSEDED'));

CREATE UNIQUE INDEX uq_meaning_current_per_suggestion
    ON meaning (suggestion_id)
    WHERE status = 'CURRENT';

CREATE TABLE corpus_example (
    id UUID PRIMARY KEY,
    suggestion_id UUID NOT NULL REFERENCES suggestion (id) ON DELETE CASCADE,
    url TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (suggestion_id, url)
);

CREATE INDEX ix_corpus_example_suggestion_created
    ON corpus_example (suggestion_id, created_at, id);

-- Earlier corpus links lived only in browser memory, so a stored FOUND value has
-- no durable evidence to support it after this migration.
UPDATE suggestion
SET corpus_status = 'NOT_FOUND'
WHERE corpus_status = 'FOUND';
