Feature: Editing drafts

  Scenario: Create and edit a new entry, lexeme and sense before completing the case
    Given a case combines suggestions "1"
    When I POST "/api/reviews/{caseId}/tezaurs-check" with:
      """
      {"status":"NOT_FOUND"}
      """
    Then the response status is 200
    When I POST "/api/reviews/{caseId}/usage/decision" with:
      """
      {"decision":"USED","note":"Valodniekam zināms vārds."}
      """
    Then the response status is 200
    When I request "/api/reviews/{caseId}/entry-draft"
    Then the response status is 200
    And the response field "/heading" equals "jaunvārds"
    And the response field "/lexemes" contains 1 items
    And I save "/lexemes/0/id" as "lexemeId"
    When I PUT "/api/reviews/{caseId}/entry-draft" with:
      """
      {"heading":"jaunvārds","typeId":1,"notes":"Precizēts šķirklis","flags":{}}
      """
    Then the response status is 200
    And the response field "/draft/notes" equals "Precizēts šķirklis"
    When I PUT "/api/reviews/{caseId}/entry-draft/lexemes/{lexemeId}" with:
      """
      {"lemma":"jaunvārds","primary":true,"roleId":1,"pronunciations":[],"flags":{"Piezīmes":"Precizēta leksēma"}}
      """
    Then the response status is 200
    And the response field "/lexeme/flags/Piezīmes" equals "Precizēta leksēma"
    When I PUT "/api/reviews/{caseId}/sense-drafts" with:
      """
      {"senses":[{"gloss":"Jauna definīcija.","flags":{}}]}
      """
    Then the response status is 200
    And the response field "/senses/0/sourceSenseId" is null
    When I POST "/api/reviews/{caseId}/status" with:
      """
      {"status":"COMPLETED"}
      """
    Then the response status is 200
    And the response field "/status" equals "COMPLETED"

  Scenario: An existing entry needs a new sense before the case can be completed
    Given a case combines suggestions "3"
    When I POST "/api/reviews/{caseId}/tezaurs-check" with:
      """
      {"status":"MEANING_NOT_FOUND","matchedEntryId":100}
      """
    Then the response status is 200
    When I POST "/api/reviews/{caseId}/status" with:
      """
      {"status":"COMPLETED"}
      """
    Then the response status is 409
    When I POST "/api/reviews/{caseId}/entry-draft" with:
      """
      {}
      """
    Then the response status is 409
    When I PUT "/api/reviews/{caseId}/sense-drafts" with:
      """
      {"senses":[{"gloss":"Pievienotā nozīme.","flags":{}}]}
      """
    Then the response status is 200
    And the response field "/senses/0/sourceSenseId" is null
    And the response field "/senses/0/parentTezaursSenseId" is null
    And the response field "/senses/0/parentDraftSenseId" is null
    When I POST "/api/reviews/{caseId}/status" with:
      """
      {"status":"COMPLETED"}
      """
    Then the response status is 200
    When I request "/api/tezaurs/entries/100"
    Then the response status is 200
    And the response field "/senses" contains 2 items
    And the response field "/senses/1/parentSenseId" is 300

  Scenario: Reopen an already-existing meaning and edit only its sense draft
    Given a review case with status "ALREADY_EXISTS"
    When I POST "/api/reviews/{caseId}/tezaurs-check" with:
      """
      {"status":"MEANING_NEEDS_EDITING","matchedEntryId":100}
      """
    Then the response status is 200
    And the response field "/status" equals "IN_PROGRESS"
    When I request "/api/reviews/{caseId}/sense-drafts"
    Then the response status is 200
    And the response field "" contains 0 items
    When I POST "/api/reviews/{caseId}/status" with:
      """
      {"status":"COMPLETED"}
      """
    Then the response status is 409
    When I PUT "/api/reviews/{caseId}/sense-drafts" with:
      """
      {"senses":[{"sourceSenseId":300,"gloss":"Precizēta definīcija.","flags":{"Piezīmes":"Precizēta pazīme"}}]}
      """
    Then the response status is 200
    And the response field "/senses/0/sourceSenseId" is 300
    And I save "/senses/0/id" as "senseId"
    When I PUT "/api/reviews/{caseId}/sense-drafts" with:
      """
      {"senses":[{"id":"{senseId}","sourceSenseId":300,"gloss":"Atkārtoti precizēta definīcija.","flags":{}}]}
      """
    Then the response status is 200
    And the response field "/senses" contains 1 items
    And the response field "/senses/0/id" equals "{senseId}"
    When I POST "/api/reviews/{caseId}/status" with:
      """
      {"status":"COMPLETED"}
      """
    Then the response status is 200
    When I request "/api/tezaurs/entries/100"
    Then the response status is 200
    And the response field "/senses/0/gloss" equals "Pieņemt lēmumu."

  Scenario: Switching the selected entry preserves each branch's saved sense drafts
    Given a case combines suggestions "1"
    When I POST "/api/reviews/{caseId}/tezaurs-check" with:
      """
      {"status":"NOT_FOUND"}
      """
    Then the response status is 200
    When I POST "/api/reviews/{caseId}/usage/decision" with:
      """
      {"decision":"USED","note":"Valodniekam zināms vārds."}
      """
    Then the response status is 200
    When I PUT "/api/reviews/{caseId}/sense-drafts" with:
      """
      {"senses":[{"gloss":"Jaunā šķirkļa nozīme.","flags":{}}]}
      """
    Then the response status is 200
    When I POST "/api/reviews/{caseId}/tezaurs-check" with:
      """
      {"status":"MEANING_NOT_FOUND","matchedEntryId":100}
      """
    Then the response status is 200
    When I PUT "/api/reviews/{caseId}/sense-drafts" with:
      """
      {"senses":[{"gloss":"Esošā šķirkļa jaunā nozīme.","flags":{}}]}
      """
    Then the response status is 200
    When I POST "/api/reviews/{caseId}/tezaurs-check" with:
      """
      {"status":"NOT_FOUND"}
      """
    Then the response status is 200
    When I POST "/api/reviews/{caseId}/usage/decision" with:
      """
      {"decision":"USED","note":"Valodniekam zināms vārds."}
      """
    Then the response status is 200
    When I request "/api/reviews/{caseId}/sense-drafts"
    Then the response status is 200
    And the response field "/0/gloss" equals "Jaunā šķirkļa nozīme."
