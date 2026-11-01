Feature: Backend API

  Scenario: The backend and its database are available
    When I request "/actuator/health"
    Then the response status is 200
    And the service reports that it is healthy

  Scenario Outline: Unstarted suggestions are paginated
    When I request "/api/suggestions?page=0&size=2&sort=<sort>"
    Then the response status is 200
    And the response is page 0 with a page size of 2

    Examples:
      | sort   |
      | NEWEST |
      | OLDEST |

  Scenario Outline: Review cases are filtered by status and counted consistently
    Given a review case with status "<status>"
    When I request "/api/reviews?status=<status>&page=0&size=2&sort=MOST_SUGGESTIONS"
    Then the response status is 200
    And the response is page 0 with a page size of 2
    And every returned case has status "<status>"
    And the response field "/totalItems" is 1
    And each returned case's suggestion count matches its details

    Examples:
      | status            |
      | READY_FOR_REVIEW  |
      | IN_PROGRESS       |
      | GARBAGE           |
      | COMPLETED         |
      | INVENTED          |
      | ALREADY_EXISTS    |
      | INSUFFICIENT_DATA |
      | NEEDS_EXPERT      |

  Scenario Outline: Invalid pagination is rejected
    When I request "<path>"
    Then the response status is 400

    Examples:
      | path                                                   |
      | /api/suggestions?page=-1                                |
      | /api/suggestions?size=0                                 |
      | /api/reviews?status=IN_PROGRESS&page=-1                   |
      | /api/reviews?status=IN_PROGRESS&size=0                    |
