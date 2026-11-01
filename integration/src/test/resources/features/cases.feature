Feature: Combining and separating suggestions

  Scenario: Merging into a completed case preserves its status and detaching returns a suggestion to the queue
    Given a review case with status "COMPLETED"
    Then I save "/id" as "destinationCase"
    Given a case combines suggestions "1"
    Then I save "/id" as "oldCase"
    When I POST "/api/suggestions/1/duplicate" with:
      """
      {"reviewId":"{destinationCase}"}
      """
    Then the response status is 200
    And the response field "/status" equals "COMPLETED"
    And the response field "/sources" contains 2 items
    When I request "/api/reviews/{oldCase}"
    Then the response status is 404
    When I DELETE "/api/reviews/{destinationCase}/suggestions/1"
    Then the response status is 200
    And the response field "/status" equals "COMPLETED"
    And the response field "/sources" contains 1 items
    When I request "/api/suggestions?sort=OLDEST&size=1"
    Then the response status is 200
    And the response field "/items/0/id" is 1
    When I DELETE "/api/reviews/{destinationCase}/suggestions/3"
    Then the response status is 409
