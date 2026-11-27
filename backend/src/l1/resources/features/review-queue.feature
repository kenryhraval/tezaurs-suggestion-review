Feature: Review queue

  Scenario Outline: An imported suggestion is queued as a new review
    Given the table "incubator.suggestions" contains:
      | id             | lemma   | gloss   |
      | <suggestionId> | <lemma> | <gloss> |
    When the scheduled import has run
    And I GET "/api/reviews?status=NEW"
    Then the response status is 200
    And the response contains:
      """
      {"items": [{"reviewedTerm": "<lemma>", "status": "NEW", "sourceCount": 1}]}
      """

    Examples:
      | suggestionId | lemma     | gloss        |
      | 1            | jaunvārds | Jauns vārds. |

  Scenario Outline: Editors find a review by a word from its suggested definition
    Given the table "incubator.suggestions" contains:
      | id                  | lemma        | gloss        |
      | <suggestionId>      | <lemma>      | <gloss>      |
      | <otherSuggestionId> | <otherLemma> | <otherGloss> |
    And the table "review.cases" contains:
      | id            | public_id       | reviewed_term | status | type    |
      | <reviewId>      | <publicId>      | <lemma>       | NEW    | UNKNOWN |
      | <otherReviewId> | <otherPublicId> | <otherLemma>  | NEW    | UNKNOWN |
    And the table "review.case_suggestions" contains:
      | source_suggestion_id | case_id       |
      | <suggestionId>       | <reviewId>      |
      | <otherSuggestionId>  | <otherReviewId> |
    When I GET "/api/reviews?query=<query>"
    Then the response status is 200
    And the response contains:
      """
      {"items": [{"id": "<otherReviewId>"}], "totalItems": 1}
      """

    Examples:
      | reviewId                               | otherReviewId                          | suggestionId | otherSuggestionId | publicId | otherPublicId | lemma | otherLemma | gloss          | otherGloss    | query  |
      | 00000000-0000-0000-0000-000000000001 | 00000000-0000-0000-0000-000000000002 | 1            | 2                 | case001  | case002       | alfa  | beta       | Pirmais burts. | Otrais burts. | otrais |

  Scenario Outline: Editors see only the reviews in the chosen status
    Given the table "incubator.suggestions" contains:
      | id                  | lemma        | gloss |
      | <suggestionId>      | <lemma>      | -     |
      | <otherSuggestionId> | <otherLemma> | -     |
    And the table "review.cases" contains:
      | id            | public_id       | reviewed_term | status      | type    |
      | <reviewId>      | <publicId>      | <lemma>       | NEW         | UNKNOWN |
      | <otherReviewId> | <otherPublicId> | <otherLemma>  | IN_PROGRESS | UNKNOWN |
    And the table "review.case_suggestions" contains:
      | source_suggestion_id | case_id       |
      | <suggestionId>       | <reviewId>      |
      | <otherSuggestionId>  | <otherReviewId> |
    When I GET "/api/reviews?status=IN_PROGRESS"
    Then the response status is 200
    And the response contains:
      """
      {"items": [{"id": "<otherReviewId>"}], "totalItems": 1}
      """

    Examples:
      | reviewId                               | otherReviewId                          | suggestionId | otherSuggestionId | publicId | otherPublicId | lemma | otherLemma |
      | 00000000-0000-0000-0000-000000000001 | 00000000-0000-0000-0000-000000000002 | 1            | 2                 | case001  | case002       | alfa  | beta       |

  Scenario: A shared review link survives a title change
    Given the table "incubator.suggestions" contains:
      | id | lemma | gloss       |
      | 1  | vārds | Skaidrojums |
    And the table "review.cases" contains:
      | id                                   | public_id | reviewed_term | status      | type    |
      | 00000000-0000-0000-0000-000000000001 | case001   | vārds         | IN_PROGRESS | UNKNOWN |
    And the table "review.case_suggestions" contains:
      | source_suggestion_id | case_id                              |
      | 1                    | 00000000-0000-0000-0000-000000000001 |
    When I GET "/api/reviews/by-public-id/case001"
    Then the response status is 200
    And the response contains:
      """
      {"id":"00000000-0000-0000-0000-000000000001","publicId":"case001","reviewedTerm":"vārds"}
      """
    When I POST "/api/reviews/00000000-0000-0000-0000-000000000001/term-correction" with:
      """
      {"reviewedTerm":"Labots vārds"}
      """
    Then the response status is 200
    When I GET "/api/reviews/by-public-id/case001"
    Then the response status is 200
    And the response contains:
      """
      {"id":"00000000-0000-0000-0000-000000000001","publicId":"case001","reviewedTerm":"Labots vārds"}
      """
    When I GET "/api/reviews/by-public-id/absent"
    Then the response status is 404
