Feature: Duplicate suggestions

  Background:
    Given the table "incubator.suggestions" contains:
      | id | lemma     | gloss                |
      | 1  | jaunvārds | Pirmais skaidrojums. |
      | 2  | jaunvards | Otrais skaidrojums.  |
    And the table "review.cases" contains:
      | id                                   | public_id | reviewed_term | status | type    |
      | 00000000-0000-0000-0000-000000000001 | case001   | jaunvārds     | NEW    | UNKNOWN |
      | 00000000-0000-0000-0000-000000000002 | case002   | jaunvards     | NEW    | UNKNOWN |
    And the table "review.case_suggestions" contains:
      | source_suggestion_id | case_id                              |
      | 1                    | 00000000-0000-0000-0000-000000000001 |
      | 2                    | 00000000-0000-0000-0000-000000000002 |

  Scenario Outline: Merging duplicates keeps one review with all suggestions
    When I POST "/api/reviews/<otherReviewId>/duplicate" with:
      """
      {"reviewId": "<reviewId>"}
      """
    Then the response status is 200
    And the response contains:
      """
      {"id": "<reviewId>", "sources": [{"id": <suggestionId>}, {"id": <otherSuggestionId>}]}
      """
    When I GET "/api/reviews/<otherReviewId>"
    Then the response status is 404

    Examples:
      | reviewId                               | otherReviewId                          | suggestionId | otherSuggestionId |
      | 00000000-0000-0000-0000-000000000001 | 00000000-0000-0000-0000-000000000002 | 1            | 2                 |


  Scenario Outline: A wrongly merged suggestion can be separated into a new review
    Given I POST "/api/reviews/<otherReviewId>/duplicate" with:
      """
      {"reviewId": "<reviewId>"}
      """
    When I DELETE "/api/reviews/<reviewId>/suggestions/<otherSuggestionId>"
    Then the response status is 200
    And the response contains:
      """
      {"sources": [{"id": <suggestionId>}]}
      """
    When I GET "/api/reviews?query=<lemma>"
    Then the response contains:
      """
      {"items": [{"reviewedTerm": "<lemma>", "status": "NEW", "sourceCount": 1}]}
      """

    Examples:
      | reviewId                               | otherReviewId                          | suggestionId | otherSuggestionId | lemma     |
      | 00000000-0000-0000-0000-000000000001 | 00000000-0000-0000-0000-000000000002 | 1            | 2                 | jaunvards |
