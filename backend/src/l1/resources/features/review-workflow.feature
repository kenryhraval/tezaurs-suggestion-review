Feature: Review workflow

  Background:
    Given the table "incubator.suggestions" contains:
      | id | lemma     | gloss        |
      | 1  | jaunvārds | Jauns vārds. |

  Scenario Outline: Taking a review into work records the editor
    Given the table "review.cases" contains:
      | id       | public_id  | reviewed_term | status | type    |
      | <reviewId> | <publicId> | <lemma>       | NEW    | UNKNOWN |
    And the table "review.case_suggestions" contains:
      | source_suggestion_id | case_id  |
      | <suggestionId>       | <reviewId> |
    When I POST "/api/reviews/<reviewId>/status" with:
      """
      {"status": "IN_PROGRESS"}
      """
    Then the response status is 200
    And the response contains:
      """
      {"status": "IN_PROGRESS", "lastStatusChangedBy": "redaktors"}
      """

    Examples:
      | reviewId                               | suggestionId | publicId | lemma     |
      | 00000000-0000-0000-0000-000000000001 | 1            | case001  | jaunvārds |

  Scenario Outline: A new review cannot be completed without review
    Given the table "review.cases" contains:
      | id       | public_id  | reviewed_term | status | type    |
      | <reviewId> | <publicId> | <lemma>       | NEW    | UNKNOWN |
    And the table "review.case_suggestions" contains:
      | source_suggestion_id | case_id  |
      | <suggestionId>       | <reviewId> |
    When I POST "/api/reviews/<reviewId>/status" with:
      """
      {"status": "COMPLETED"}
      """
    Then the response status is 409
    When I GET "/api/reviews/<reviewId>"
    Then the response contains:
      """
      {"status": "NEW"}
      """

    Examples:
      | reviewId                               | suggestionId | publicId | lemma     |
      | 00000000-0000-0000-0000-000000000001 | 1            | case001  | jaunvārds |

  Scenario Outline: A completed review is final
    Given the table "review.cases" contains:
      | id       | public_id  | reviewed_term | status    | type    |
      | <reviewId> | <publicId> | <lemma>       | COMPLETED | UNKNOWN |
    And the table "review.case_suggestions" contains:
      | source_suggestion_id | case_id  |
      | <suggestionId>       | <reviewId> |
    When I POST "/api/reviews/<reviewId>/status" with:
      """
      {"status": "IN_PROGRESS"}
      """
    Then the response status is 409

    Examples:
      | reviewId                               | suggestionId | publicId | lemma     |
      | 00000000-0000-0000-0000-000000000001 | 1            | case001  | jaunvārds |
