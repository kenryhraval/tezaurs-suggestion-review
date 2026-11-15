INSERT INTO review.cases (id, reviewed_term, status, type, created_at, updated_at)
SELECT fixture.case_id, source.lemma, 'NEW', 'UNKNOWN', source.created_at, source.created_at
FROM (VALUES
    (1, '00000000-0000-0000-0000-000000000001'::uuid),
    (2, '00000000-0000-0000-0000-000000000002'::uuid),
    (3, '00000000-0000-0000-0000-000000000003'::uuid),
    (4, '00000000-0000-0000-0000-000000000004'::uuid),
    (5, '00000000-0000-0000-0000-000000000005'::uuid),
    (6, '00000000-0000-0000-0000-000000000006'::uuid),
    (7, '00000000-0000-0000-0000-000000000007'::uuid)
) AS fixture(source_id, case_id)
JOIN incubator.suggestions source ON source.id = fixture.source_id;

INSERT INTO review.case_suggestions (source_suggestion_id, case_id, created_at) VALUES
    (1, '00000000-0000-0000-0000-000000000001', now()),
    (2, '00000000-0000-0000-0000-000000000002', now()),
    (3, '00000000-0000-0000-0000-000000000003', now()),
    (4, '00000000-0000-0000-0000-000000000004', now()),
    (5, '00000000-0000-0000-0000-000000000005', now()),
    (6, '00000000-0000-0000-0000-000000000006', now()),
    (7, '00000000-0000-0000-0000-000000000007', now());
