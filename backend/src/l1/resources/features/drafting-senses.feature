Feature: Drafting senses

  Background:
    Given the table "dict.entries" contains:
      | id  | type_id | homonym_no | heading |
      | 100 | 1       | 1          | lemt    |
    And the table "dict.senses" contains:
      | id  | entry_id | parent_sense_id | gloss                 | order_no |
      | 300 | 100      |                 | Pieņemt lēmumu.       | 1        |
      | 301 | 100      | 300             | Pieņemt lēmumu tiesā. | 1        |
    And the table "incubator.suggestions" contains:
      | id | lemma | gloss            |
      | 1  | lemt  | Izšķirt likteni. |
    And the table "review.cases" contains:
      | id                                   | public_id | reviewed_term | status      | type  | matched_entry_id |
      | 00000000-0000-0000-0000-000000000001 | case001   | lemt          | IN_PROGRESS | SENSE | 100              |
    And the table "review.case_suggestions" contains:
      | source_suggestion_id | case_id                              |
      | 1                    | 00000000-0000-0000-0000-000000000001 |
    And the table "review.senses" contains:
      | id                                   | case_id                              | target_entry_id | parent_sense_id                      | gloss                 | existing_sense_id | order_no |
      | 00000000-0000-0000-0000-000000000301 | 00000000-0000-0000-0000-000000000001 | 100             |                                      | Pieņemt lēmumu.       | 300               | 1        |
      | 00000000-0000-0000-0000-000000000302 | 00000000-0000-0000-0000-000000000001 | 100             | 00000000-0000-0000-0000-000000000301 | Pieņemt lēmumu tiesā. | 301               | 2        |
      | 00000000-0000-0000-0000-000000000303 | 00000000-0000-0000-0000-000000000001 | 100             |                                      | Izšķirt likteni.      |                   | 3        |
    And the table "review.examples" contains:
      | id                                   | case_id                              | source   | search_query | citation                    | url                |
      | 00000000-0000-0000-0000-000000000401 | 00000000-0000-0000-0000-000000000001 | EXTERNAL | lemt         | Viņš lēma par mūsu likteni. | https://example.lv |

  Scenario Outline: Senses have at most two levels
    When I POST "/api/reviews/<reviewId>/senses" with:
      """
      {"gloss": "<gloss>", "parentSenseId": "<childSenseId>"}
      """
    Then the response status is 409

    Examples:
      | reviewId                               | childSenseId                         | gloss            |
      | 00000000-0000-0000-0000-000000000001 | 00000000-0000-0000-0000-000000000302 | Trešais līmenis. |

  Scenario Outline: Existing Tēzaurs senses are read-only
    When I PUT "/api/reviews/<reviewId>/senses/<parentSenseId>" with:
      """
      {"gloss": "<gloss>"}
      """
    Then the response status is 409

    Examples:
      | reviewId                               | parentSenseId                        | gloss             |
      | 00000000-0000-0000-0000-000000000001 | 00000000-0000-0000-0000-000000000301 | Cits skaidrojums. |

  Scenario Outline: An example is approved for the sense it illustrates
    When I PUT "/api/reviews/<reviewId>/usage/examples/<exampleId>" with:
      """
      {"senseId": "<newSenseId>", "approved": true}
      """
    Then the response status is 200
    And the response contains:
      """
      {"id": "<exampleId>", "senseId": "<newSenseId>", "approved": true}
      """

    Examples:
      | reviewId                               | newSenseId                           | exampleId                            |
      | 00000000-0000-0000-0000-000000000001 | 00000000-0000-0000-0000-000000000303 | 00000000-0000-0000-0000-000000000401 |


  Scenario Outline: A review cannot be completed without an approved new sense
    When I POST "/api/reviews/<reviewId>/status" with:
      """
      {"status": "COMPLETED"}
      """
    Then the response status is 409

    Examples:
      | reviewId                               |
      | 00000000-0000-0000-0000-000000000001 |


  Scenario Outline: A review with an approved new sense can be completed
    Given I PUT "/api/reviews/<reviewId>/senses/<newSenseId>" with:
      """
      {"gloss": "<gloss>", "approved": true}
      """
    When I POST "/api/reviews/<reviewId>/status" with:
      """
      {"status": "COMPLETED"}
      """
    Then the response status is 200
    And the response contains:
      """
      {"status": "COMPLETED"}
      """

    Examples:
      | reviewId                               | newSenseId                           | gloss            |
      | 00000000-0000-0000-0000-000000000001 | 00000000-0000-0000-0000-000000000303 | Izšķirt likteni. |
