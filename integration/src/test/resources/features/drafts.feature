Feature: Editing drafts

  Scenario: Create and edit a new entry, lexeme and sense before completing the case
    Given a case combines suggestions "1"
    When I POST "/api/reviews/{caseId}/type" with:
      """
      {"type":"ENTRY"}
      """
    Then the response status is 200
    When I POST "/api/reviews/{caseId}/usage/decision" with:
      """
      {"decision":"NOT_CONFIRMED"}
      """
    Then the response status is 200
    And the response field "/status" equals "INSUFFICIENT_DATA"
    When I POST "/api/reviews/{caseId}/usage/decision" with:
      """
      {"decision":"USED"}
      """
    Then the response status is 409
    When I POST "/api/reviews/{caseId}/status" with:
      """
      {"status":"IN_PROGRESS"}
      """
    Then the response status is 200
    And the response field "/usageDecision" equals "NOT_CONFIRMED"
    When I POST "/api/reviews/{caseId}/usage/decision" with:
      """
      {"decision":"USED","note":"Valodniekam zināms vārds."}
      """
    Then the response status is 200
    When I request "/api/reviews/{caseId}/entry"
    Then the response status is 200
    And the response field "/heading" equals "jaunvārds"
    And the response field "/lexemes" contains 1 items
    And I save "/lexemes/0/id" as "lexemeId"
    When I PUT "/api/reviews/{caseId}/entry" with:
      """
      {"heading":"jaunvārds","type":"WORD","notes":"Precizēts šķirklis","flags":{}}
      """
    Then the response status is 200
    And the response field "/draft/notes" equals "Precizēts šķirklis"
    When I PUT "/api/reviews/{caseId}/entry/lexemes/{lexemeId}" with:
      """
      {"lemma":"jaunvārds","primary":true,"roleId":1,"pronunciations":[],"flags":{"Piezīmes":"Precizēta leksēma"}}
      """
    Then the response status is 200
    And the response field "/lexeme/flags/Piezīmes" equals "Precizēta leksēma"
    When I POST "/api/reviews/{caseId}/senses" with:
      """
      {"approved":true,"gloss":"Jauna definīcija.","flags":{}}
      """
    Then the response status is 200
    When I POST "/api/reviews/{caseId}/status" with:
      """
      {"status":"COMPLETED"}
      """
    Then the response status is 200
    And the response field "/status" equals "COMPLETED"

  Scenario: An existing entry needs a new sense before the case can be completed
    Given a case combines suggestions "3"
    When I request "/api/tezaurs/entries?query=lemt"
    Then the response status is 200
    And the response field "/0/id" is 100
    And the response field "/0/senses" contains 2 items
    And the response field "/0/senses/0/id" is 300
    And the response field "/0/senses/1/parentSenseId" is 300
    When I POST "/api/reviews/{caseId}/type" with:
      """
      {"type":"SENSE","matchedEntryId":100}
      """
    Then the response status is 200
    When I POST "/api/reviews/{caseId}/status" with:
      """
      {"status":"COMPLETED"}
      """
    Then the response status is 409
    When I POST "/api/reviews/{caseId}/entry" with:
      """
      {}
      """
    Then the response status is 409
    When I POST "/api/reviews/{caseId}/senses" with:
      """
      {"approved":true,"gloss":"Pievienotā nozīme.","flags":{}}
      """
    Then the response status is 200
    And the response field "/sense/parentTezaursSenseId" is null
    And the response field "/sense/parentDraftSenseId" is null
    When I POST "/api/reviews/{caseId}/status" with:
      """
      {"status":"COMPLETED"}
      """
    Then the response status is 200
    When I request "/api/tezaurs/entries/100/senses"
    Then the response status is 200
    And the response field "" contains 2 items
    And the response field "/1/parentSenseId" is 300

  Scenario: Switching the selected entry preserves each branch's saved sense drafts
    Given a case combines suggestions "1"
    When I POST "/api/reviews/{caseId}/type" with:
      """
      {"type":"ENTRY"}
      """
    Then the response status is 200
    When I POST "/api/reviews/{caseId}/usage/decision" with:
      """
      {"decision":"USED","note":"Valodniekam zināms vārds."}
      """
    Then the response status is 200
    When I POST "/api/reviews/{caseId}/senses" with:
      """
      {"approved":true,"gloss":"Jaunā šķirkļa nozīme.","flags":{}}
      """
    Then the response status is 200
    When I POST "/api/reviews/{caseId}/type" with:
      """
      {"type":"SENSE","matchedEntryId":100}
      """
    Then the response status is 200
    When I POST "/api/reviews/{caseId}/senses" with:
      """
      {"approved":true,"gloss":"Esošā šķirkļa jaunā nozīme.","flags":{}}
      """
    Then the response status is 200
    When I POST "/api/reviews/{caseId}/type" with:
      """
      {"type":"ENTRY"}
      """
    Then the response status is 200
    When I POST "/api/reviews/{caseId}/usage/decision" with:
      """
      {"decision":"USED","note":"Valodniekam zināms vārds."}
      """
    Then the response status is 200
    When I request "/api/reviews/{caseId}/senses"
    Then the response status is 200
    And the response field "/0/gloss" equals "Jaunā šķirkļa nozīme."
