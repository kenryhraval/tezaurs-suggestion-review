@import
Feature: Importing submitted suggestions

  Scenario: The scheduled importer preserves submissions and imports later arrivals once
    When the scheduled importer processes the unlinked suggestions
    Then the cases preserve the submitted terms and original statuses without an author
    When another suggestion is submitted
    Then it receives a new case without changing the existing cases
