Feature: Checking Tēzaurs

  Background:
    Given the table "dict.entries" contains:
      | id  | type_id | homonym_no | heading |
      | 100 | 1       | 1          | lemt    |
    And the table "dict.senses" contains:
      | id  | entry_id | parent_sense_id | gloss                 | order_no |
      | 300 | 100      |                 | Pieņemt lēmumu.       | 1        |
      | 301 | 100      | 300             | Pieņemt lēmumu tiesā. | 1        |
    And the table "incubator.suggestions" contains:
      | id | lemma     | gloss            |
      | 1  | lemt      | Izšķirt likteni. |
      | 2  | jaunvārds | Jauns vārds.     |
    And the table "review.cases" contains:
      | id                                   | public_id | reviewed_term | status      | type    |
      | 00000000-0000-0000-0000-000000000001 | case001   | lemt          | IN_PROGRESS | UNKNOWN |
      | 00000000-0000-0000-0000-000000000002 | case002   | jaunvārds     | IN_PROGRESS | UNKNOWN |
    And the table "review.case_suggestions" contains:
      | source_suggestion_id | case_id                              |
      | 1                    | 00000000-0000-0000-0000-000000000001 |
      | 2                    | 00000000-0000-0000-0000-000000000002 |

  Scenario Outline: A new sense is drafted next to the existing senses of the entry
    When I POST "/api/reviews/<reviewId>/type" with:
      """
      {"type": "SENSE", "matchedEntryId": <legacyEntryId>}
      """
    Then the response status is 200
    When I GET "/api/reviews/<reviewId>/senses"
    Then the response contains:
      """
      [
        {"gloss": "<parentGloss>", "existingSenseId": <existingParentSenseId>, "parentSenseId": null},
        {"gloss": "<childGloss>", "existingSenseId": <existingChildSenseId>},
        {"gloss": "<newGloss>", "existingSenseId": null, "approved": false}
      ]
      """
    And Tēzaurs sense <existingChildSenseId> is a subsense of Tēzaurs sense <existingParentSenseId> in the draft

    Examples:
      | reviewId                               | legacyEntryId | existingParentSenseId | existingChildSenseId | parentGloss     | childGloss            | newGloss         |
      | 00000000-0000-0000-0000-000000000001 | 100           | 300                   | 301                  | Pieņemt lēmumu. | Pieņemt lēmumu tiesā. | Izšķirt likteni. |

  Scenario Outline: A sense that Tēzaurs already has is marked as existing
    When I POST "/api/reviews/<reviewId>/type" with:
      """
      {"type": "SENSE", "matchedEntryId": <legacyEntryId>, "meaningAlreadyExists": true}
      """
    Then the response status is 200
    And the response contains:
      """
      {"status": "ALREADY_EXISTS", "type": "SENSE", "matchedEntryId": <legacyEntryId>}
      """

    Examples:
      | reviewId                               | legacyEntryId |
      | 00000000-0000-0000-0000-000000000001 | 100           |


  Scenario Outline: Confirming that a new word is used starts an entry draft with the next homonym number
    Given I POST "/api/reviews/<review>/type" with:
      """
      {"type": "ENTRY"}
      """
    When I POST "/api/reviews/<review>/usage/decision" with:
      """
      {"decision": "USED"}
      """
    Then the response status is 200
    When I GET "/api/reviews/<review>/entry"
    Then the response status is 200
    And the response contains:
      """
      {"humanKey": "<draft>", "lexemes": [{"lemma": "<term>", "primary": true}]}
      """

    Examples:
      | review                                 | term      | draft       |
      | 00000000-0000-0000-0000-000000000002 | jaunvārds | jaunvārds:1 |
      | 00000000-0000-0000-0000-000000000001 | lemt      | lemt:2      |


  Scenario Outline: A word whose usage cannot be confirmed waits for more data
    Given I POST "/api/reviews/<otherReviewId>/type" with:
      """
      {"type": "ENTRY"}
      """
    When I POST "/api/reviews/<otherReviewId>/usage/decision" with:
      """
      {"decision": "NOT_CONFIRMED"}
      """
    Then the response status is 200
    And the response contains:
      """
      {"status": "INSUFFICIENT_DATA", "usageDecision": "NOT_CONFIRMED", "usageDecidedBy": "redaktors"}
      """
    When I GET "/api/reviews/<otherReviewId>/entry"
    Then the response status is 204

    Examples:
      | otherReviewId                          |
      | 00000000-0000-0000-0000-000000000002 |
