TRUNCATE review.cases, incubator.suggestions, dict.entries, dict.lexemes, dict.senses,
    dict.examples, dict.entry_types, dict.lexeme_roles, dict.paradigms,
    dict.grammar_flags, dict.grammar_flag_values, dict.grammar_restriction_types,
    dict.grammar_restriction_frequencies, dict.sources, dict.source_links CASCADE;

INSERT INTO dict.entry_types (id, name, description) VALUES (1, 'word', 'Vārds');
INSERT INTO dict.lexeme_roles (id, name, description) VALUES (1, 'main', 'Pamatleksēma');
INSERT INTO dict.paradigms (id, human_key, caption, stems) VALUES (15, 'verb-1', 'Darbības vārds', 3);
INSERT INTO dict.grammar_flags (id, name, caption, permitted_values, scope)
    VALUES (1, 'Piezīmes', 'Piezīmes', 'T', ARRAY['E', 'L', 'S', 'R']);

INSERT INTO dict.entries (id, type_id, homonym_no, heading) VALUES (100, 1, 1, 'lemt');
INSERT INTO dict.lexemes (id, entry_id, role_id, lemma, paradigm_id, stem1, stem2, stem3)
    VALUES (200, 100, 1, 'lemt', 15, 'lem', 'lemj', 'lēm');
UPDATE dict.entries SET primary_lexeme_id = 200 WHERE id = 100;
INSERT INTO dict.senses (id, entry_id, gloss, order_no, data)
    VALUES (300, 100, 'Pieņemt lēmumu.', 1, '{"Gram":{"Flags":{"Piezīmes":"Sākotnējā pazīme"}}}');
INSERT INTO dict.senses (id, entry_id, parent_sense_id, gloss, order_no)
    VALUES (301, 100, 300, 'Pieņemt lēmumu tiesā.', 1);

INSERT INTO incubator.suggestions (id, lemma, gloss, created_at) VALUES
    (1, 'jaunvārds', 'Meklējamais skaidrojums.', '2026-01-01T10:00:00Z'),
    (2, 'jaunvārds', 'Cits ieteiktais skaidrojums.', '2026-01-02T10:00:00Z'),
    (3, 'lemt', 'Jauna nozīme.', '2026-01-03T10:00:00Z'),
    (4, 'lemt', 'Pieņemt lēmumu.', '2026-01-04T10:00:00Z'),
    (5, 'jaunvārds', 'Vēl viens ieteikums.', '2026-01-05T10:00:00Z'),
    (6, 'jaunvārds', 'Papildu skaidrojums.', '2026-01-06T10:00:00Z'),
    (7, 'jaunvārds', 'Piektais ieteikums.', '2026-01-07T10:00:00Z');
