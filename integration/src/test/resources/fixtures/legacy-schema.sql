-- Legacy table definitions from the Tēzaurs dump, limited to tables mapped by the backend.
-- Application tables are created separately by the real Flyway migration.

CREATE SCHEMA dict;

CREATE SCHEMA incubator;

CREATE COLLATION dict.latviski (provider = icu, locale = 'lv-LV');

CREATE TYPE dict.hiding_reasons AS ENUM (
    'not-ready',
    'not-verified',
    'not-public'
);

CREATE TABLE dict.entries (
    id integer NOT NULL,
    type_id integer NOT NULL,
    homonym_no integer DEFAULT 0 NOT NULL,
    heading text NOT NULL COLLATE dict.latviski,
    data jsonb,
    primary_lexeme_id integer,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now(),
    updated_by integer DEFAULT 1,
    notes text,
    hidden boolean DEFAULT false NOT NULL,
    heading_is_primary_lexeme boolean DEFAULT true NOT NULL,
    human_key text GENERATED ALWAYS AS (
CASE
    WHEN (type_id = 4) THEN ('mwe:'::text || (id)::text)
    ELSE ((heading || ':'::text) || (homonym_no)::text)
END) STORED,
    ts timestamp with time zone DEFAULT now(),
    extras jsonb,
    reason_for_hiding dict.hiding_reasons,
    no_corporum_examples boolean DEFAULT false NOT NULL,
    shape_id integer GENERATED ALWAYS AS (
CASE
    WHEN (("left"(heading, 1) = '-'::text) OR ("right"(heading, 1) = '-'::text)) THEN 5
    WHEN (POSITION((' '::text) IN (heading)) > 0) THEN 4
    ELSE 1
END) STORED NOT NULL,
    CONSTRAINT entries_hidden_reason_ck CHECK ((((NOT hidden) AND (reason_for_hiding IS NULL)) OR (hidden AND (reason_for_hiding IS NOT NULL))))
);

ALTER TABLE dict.entries ADD PRIMARY KEY (id);

CREATE TABLE dict.entry_types (
    id integer NOT NULL,
    name text NOT NULL,
    description text
);

ALTER TABLE dict.entry_types ADD PRIMARY KEY (id);

CREATE TABLE dict.examples (
    id integer NOT NULL,
    sense_id integer,
    entry_id integer,
    content text NOT NULL COLLATE dict.latviski,
    data jsonb,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now(),
    updated_by integer DEFAULT 1,
    order_no integer,
    hidden boolean DEFAULT false NOT NULL,
    reason_for_hiding dict.hiding_reasons,
    CONSTRAINT examples_hidden_reason_ck CHECK ((((NOT hidden) AND (reason_for_hiding IS NULL)) OR (hidden AND (reason_for_hiding IS NOT NULL))))
);

ALTER TABLE dict.examples ADD PRIMARY KEY (id);

CREATE TABLE dict.grammar_flags (
    id integer NOT NULL,
    name text NOT NULL,
    description text,
    caption text,
    is_deprecated boolean DEFAULT false NOT NULL,
    is_multiple boolean DEFAULT false NOT NULL,
    permitted_values text DEFAULT 'E'::text NOT NULL,
    order_no integer DEFAULT 99,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
    scope text[] DEFAULT '{E,S,L}'::text[]
);

ALTER TABLE dict.grammar_flags ADD PRIMARY KEY (id);

CREATE TABLE dict.grammar_flag_values (
    id integer NOT NULL,
    flag_id integer NOT NULL,
    value text NOT NULL,
    is_deprecated boolean DEFAULT false NOT NULL,
    order_no integer DEFAULT 0,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE dict.grammar_flag_values ADD PRIMARY KEY (id);

CREATE TABLE dict.grammar_restriction_frequencies (
    id integer NOT NULL,
    name text,
    caption text,
    compare_value integer
);

ALTER TABLE dict.grammar_restriction_frequencies ADD PRIMARY KEY (id);

CREATE TABLE dict.grammar_restriction_types (
    id integer NOT NULL,
    name text,
    caption text
);

ALTER TABLE dict.grammar_restriction_types ADD PRIMARY KEY (id);

CREATE TABLE dict.lexemes (
    id integer NOT NULL,
    entry_id integer NOT NULL,
    role_id integer DEFAULT 1 NOT NULL,
    lemma text NOT NULL COLLATE dict.latviski,
    paradigm_id integer,
    stem1 text,
    stem2 text,
    stem3 text,
    data jsonb,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now(),
    updated_by integer DEFAULT 1,
    order_no integer DEFAULT 1 NOT NULL,
    hidden boolean DEFAULT false NOT NULL,
    reason_for_hiding dict.hiding_reasons,
    extras jsonb,
    shape_id integer GENERATED ALWAYS AS (
CASE
    WHEN (("left"(lemma, 1) = '-'::text) OR ("right"(lemma, 1) = '-'::text)) THEN 5
    WHEN (POSITION((' '::text) IN (lemma)) > 0) THEN 4
    ELSE 1
END) STORED NOT NULL,
    CONSTRAINT lexemes_hidden_reason_ck CHECK ((((NOT hidden) AND (reason_for_hiding IS NULL)) OR (hidden AND (reason_for_hiding IS NOT NULL))))
);

ALTER TABLE dict.lexemes ADD PRIMARY KEY (id);

CREATE TABLE dict.lexeme_roles (
    id integer NOT NULL,
    name text NOT NULL,
    description text
);

ALTER TABLE dict.lexeme_roles ADD PRIMARY KEY (id);

CREATE TABLE dict.paradigms (
    id integer NOT NULL,
    data jsonb,
    human_key text,
    legacy_no integer,
    caption text,
    caption_en text,
    stems integer DEFAULT 1 NOT NULL
);

ALTER TABLE dict.paradigms ADD PRIMARY KEY (id);

CREATE TABLE dict.senses (
    id integer NOT NULL,
    entry_id integer NOT NULL,
    parent_sense_id integer,
    gloss text COLLATE dict.latviski,
    order_no integer,
    sense_tag text,
    data jsonb,
    synset_id integer,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now(),
    updated_by integer DEFAULT 1,
    hidden boolean DEFAULT false NOT NULL,
    reason_for_hiding dict.hiding_reasons,
    CONSTRAINT senses_hidden_reason_ck CHECK ((((NOT hidden) AND (reason_for_hiding IS NULL)) OR (hidden AND (reason_for_hiding IS NOT NULL))))
);

ALTER TABLE dict.senses ADD PRIMARY KEY (id);

CREATE TABLE dict.sources (
    id integer NOT NULL,
    abbr text,
    title text,
    bib text,
    url text
);

ALTER TABLE dict.sources ADD PRIMARY KEY (id);

CREATE TABLE dict.source_links (
    id integer NOT NULL,
    source_id integer NOT NULL,
    entry_id integer NOT NULL,
    data jsonb,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone,
    updated_by integer DEFAULT 1,
    order_no integer DEFAULT 0,
    sense_id integer,
    lexeme_id integer,
    CONSTRAINT source_links_sense_lexeme_check CHECK (((sense_id IS NULL) OR (lexeme_id IS NULL)))
);

ALTER TABLE dict.source_links ADD PRIMARY KEY (id);

CREATE TABLE incubator.suggestions (
    id integer NOT NULL,
    lemma text NOT NULL,
    gloss text NOT NULL,
    example text,
    source text,
    flag_info text,
    notes text,
    contact text,
    created_by integer,
    created_at timestamp with time zone DEFAULT now(),
    status integer DEFAULT 0 NOT NULL,
    channel_id integer DEFAULT 1 NOT NULL,
    ip inet,
    updated_by integer,
    updated_at timestamp with time zone
);

ALTER TABLE incubator.suggestions ADD PRIMARY KEY (id);

ALTER TABLE ONLY dict.entries
    ADD CONSTRAINT entries_entry_types_fk FOREIGN KEY (type_id) REFERENCES dict.entry_types(id);

ALTER TABLE ONLY dict.entries
    ADD CONSTRAINT entries_primary_lexeme_fk FOREIGN KEY (primary_lexeme_id) REFERENCES dict.lexemes(id);

ALTER TABLE ONLY dict.examples
    ADD CONSTRAINT example_entry_fk FOREIGN KEY (entry_id) REFERENCES dict.entries(id) ON DELETE CASCADE;

ALTER TABLE ONLY dict.examples
    ADD CONSTRAINT example_sense_fk FOREIGN KEY (sense_id) REFERENCES dict.senses(id) ON DELETE CASCADE;

ALTER TABLE ONLY dict.grammar_flag_values
    ADD CONSTRAINT flag_value_flag_fk FOREIGN KEY (flag_id) REFERENCES dict.grammar_flags(id) ON DELETE CASCADE;

ALTER TABLE ONLY dict.lexemes
    ADD CONSTRAINT lexemes_entries_fk FOREIGN KEY (entry_id) REFERENCES dict.entries(id) ON DELETE CASCADE;

ALTER TABLE ONLY dict.lexemes
    ADD CONSTRAINT lexemes_lexeme_roles_fk FOREIGN KEY (role_id) REFERENCES dict.lexeme_roles(id);

ALTER TABLE ONLY dict.lexemes
    ADD CONSTRAINT lexemes_paradigms_fk FOREIGN KEY (paradigm_id) REFERENCES dict.paradigms(id);

ALTER TABLE ONLY dict.senses
    ADD CONSTRAINT senses_entries_fk FOREIGN KEY (entry_id) REFERENCES dict.entries(id) ON DELETE CASCADE;

ALTER TABLE ONLY dict.senses
    ADD CONSTRAINT subsense_sense_fk FOREIGN KEY (parent_sense_id) REFERENCES dict.senses(id) ON DELETE CASCADE;

ALTER TABLE ONLY dict.source_links
    ADD CONSTRAINT source_links_entries_fk FOREIGN KEY (entry_id) REFERENCES dict.entries(id) ON DELETE CASCADE;

ALTER TABLE ONLY dict.source_links
    ADD CONSTRAINT source_links_lexemes_fk FOREIGN KEY (lexeme_id) REFERENCES dict.lexemes(id) ON UPDATE CASCADE ON DELETE CASCADE;

ALTER TABLE ONLY dict.source_links
    ADD CONSTRAINT source_links_senses_fk FOREIGN KEY (sense_id) REFERENCES dict.senses(id) ON UPDATE CASCADE ON DELETE CASCADE;

ALTER TABLE ONLY dict.source_links
    ADD CONSTRAINT sources_links_sources_fk FOREIGN KEY (source_id) REFERENCES dict.sources(id) ON DELETE CASCADE;
