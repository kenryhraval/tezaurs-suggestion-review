Feature: Editing entry drafts

  Background:
    Given the table "dict.entries" contains:
      | id  | type_id | homonym_no | heading |
      | 100 | 1       | 1          | lemt    |
    And the table "dict.lexeme_roles" contains:
      | id | name   |
      | 1  | Pamata |
    And the table "incubator.suggestions" contains:
      | id | lemma     | gloss        |
      | 1  | jaunvārds | Jauns vārds. |
    And the table "review.cases" contains:
      | id                                   | public_id | reviewed_term | status      | type  | usage_decision |
      | 00000000-0000-0000-0000-000000000001 | case001   | jaunvārds     | IN_PROGRESS | ENTRY | USED           |
    And the table "review.case_suggestions" contains:
      | source_suggestion_id | case_id                              |
      | 1                    | 00000000-0000-0000-0000-000000000001 |
    And the table "review.entries" contains:
      | id                                   | case_id                              | heading   | homonym_no | type |
      | 00000000-0000-0000-0000-000000000101 | 00000000-0000-0000-0000-000000000001 | jaunvārds | 1          | WORD |
    And the table "review.lexemes" contains:
      | id                                   | entry_id                             | lemma     | primary_lexeme | role_id | order_no |
      | 00000000-0000-0000-0000-000000000201 | 00000000-0000-0000-0000-000000000101 | jaunvārds | true           | 1       | 1        |

  Scenario Outline: Entry edits are saved
    Given I PUT "/api/reviews/<reviewId>/entry" with:
      """
      {"heading": "<lemma>", "type": "WORD", "notes": "<notes>", "flags": {}}
      """
    When I GET "/api/reviews/<reviewId>/entry"
    Then the response status is 200
    And the response contains:
      """
      {"notes": "<notes>"}
      """

    Examples:
      | reviewId                               | lemma     | notes              |
      | 00000000-0000-0000-0000-000000000001 | jaunvārds | Precizēts šķirklis |

  Scenario Outline: Lexeme edits are saved
    Given I PUT "/api/reviews/<reviewId>/entry/lexemes/<lexemeId>" with:
      """
      {"lemma": "<lemma>", "primary": true, "roleId": <roleId>, "pronunciations": [], "flags": {}}
      """
    When I GET "/api/reviews/<reviewId>/entry"
    Then the response status is 200
    And the response contains:
      """
      {"lexemes": [{"id": "<lexemeId>", "lemma": "<lemma>", "primary": true}]}
      """

    Examples:
      | reviewId                               | lexemeId                             | roleId | lemma       |
      | 00000000-0000-0000-0000-000000000001 | 00000000-0000-0000-0000-000000000201 | 1      | jaunvārdiņš |

  Scenario Outline: Selecting a draft preserves its saved senses
    Given the table "review.senses" contains:
      | id              | case_id  | target_entry_id | gloss                | approved | order_no |
      | <parentSenseId> | <reviewId> |                 | <entryGloss>         | true     | 1        |
      | <childSenseId>  | <reviewId> | <legacyEntryId> | <existingEntryGloss> | true     | 1        |
    And I POST "/api/reviews/<reviewId>/type" with:
      """
      <previousSelection>
      """
    And I POST "/api/reviews/<reviewId>/type" with:
      """
      <selection>
      """
    When I GET "/api/reviews/<reviewId>/senses"
    Then the response status is 200
    And the response contains:
      """
      [{"id": "<sense>", "gloss": "<gloss>", "approved": true}]
      """

    Examples:
      | previousSelection                        | selection                                | sense                                | gloss                       | reviewId                               | parentSenseId                        | childSenseId                         | legacyEntryId | entryGloss            | existingEntryGloss          |
      | {"type": "SENSE", "matchedEntryId": 100} | {"type": "ENTRY"}                        | 00000000-0000-0000-0000-000000000301 | Jaunā šķirkļa nozīme.       | 00000000-0000-0000-0000-000000000001 | 00000000-0000-0000-0000-000000000301 | 00000000-0000-0000-0000-000000000302 | 100           | Jaunā šķirkļa nozīme. | Esošā šķirkļa jaunā nozīme. |
      | {"type": "ENTRY"}                        | {"type": "SENSE", "matchedEntryId": 100} | 00000000-0000-0000-0000-000000000302 | Esošā šķirkļa jaunā nozīme. | 00000000-0000-0000-0000-000000000001 | 00000000-0000-0000-0000-000000000301 | 00000000-0000-0000-0000-000000000302 | 100           | Jaunā šķirkļa nozīme. | Esošā šķirkļa jaunā nozīme. |
