Feature: Searching and sorting the review queue

  Scenario: A match in one suggestion still counts all five attached suggestions
    Given a case combines suggestions "1, 2, 5, 6, 7"
    When I request "/api/reviews?status=IN_PROGRESS&query=mekl&size=1&sort=MOST_SUGGESTIONS"
    Then the response status is 200
    And the response field "/totalItems" is 1
    And the response field "/items" contains 1 items
    And the response field "/items/0/sourceCount" is 5
    And each returned case's suggestion count matches its details

  Scenario Outline: Sorting and pagination count cases rather than attached suggestions
    Given a case combines suggestions "1, 2, 5, 6, 7"
    Then I save "/id" as "combinedCase"
    Given a case combines suggestions "3"
    Then I save "/id" as "singleCase"
    When I request "/api/reviews?status=IN_PROGRESS&size=1&page=0&sort=<sort>"
    Then the response status is 200
    And the response is page 0 with a page size of 1
    And the response field "/totalItems" is 2
    And the response field "/items/0/id" equals "<first>"
    When I request "/api/reviews?status=IN_PROGRESS&size=1&page=1&sort=<sort>"
    Then the response status is 200
    And the response is page 1 with a page size of 1
    And the response field "/totalItems" is 2
    And the response field "/items/0/id" equals "<second>"

    Examples:
      | sort             | first          | second         |
      | OLDEST           | {combinedCase} | {singleCase}   |
      | NEWEST           | {singleCase}   | {combinedCase} |
      | MOST_SUGGESTIONS | {combinedCase} | {singleCase}   |
