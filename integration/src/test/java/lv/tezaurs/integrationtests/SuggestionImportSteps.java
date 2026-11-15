package lv.tezaurs.integrationtests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SuggestionImportSteps {
    private static final List<String> LEGACY_STATUSES = List.of(
            "NEW", "READY_FOR_REVIEW", "GARBAGE", "COMPLETED", "IN_PROGRESS",
            "INVENTED", "ALREADY_EXISTS", "INSUFFICIENT_DATA", "NEEDS_EXPERT");
    private Map<Integer, UUID> originalCases;
    private String originalLegacyData;

    @Before("@import")
    public void resetImportScenario() throws Exception {
        TestEnvironment.resetImportData();
        originalLegacyData = TestEnvironment.legacyDataSnapshot();
    }

    @When("the scheduled importer processes the unlinked suggestions")
    public void waitForInitialImport() throws Exception {
        waitForCases(9);
    }

    @Then("the cases preserve the submitted terms and original statuses without an author")
    public void verifyInitialCases() throws SQLException {
        originalCases = verifyImportedCases(9);
    }

    @When("another suggestion is submitted")
    public void submitAnotherSuggestion() throws Exception {
        try (var connection = TestEnvironment.connection(); var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO incubator.suggestions (id, lemma, gloss, status)
                    VALUES (9, 'Vēl viens vārds', 'Skaidrojums.', 0)
                    """);
        }
        originalLegacyData = TestEnvironment.legacyDataSnapshot();
    }

    @Then("it receives a new case without changing the existing cases")
    public void verifyLaterImport() throws Exception {
        waitForCases(10);
        Map<Integer, UUID> currentCases = verifyImportedCases(10);
        for (var original : originalCases.entrySet()) {
            assertEquals(original.getValue(), currentCases.get(original.getKey()),
                    "An existing case was recreated");
        }
    }

    private Map<Integer, UUID> verifyImportedCases(int expectedCount) throws SQLException {
        Map<Integer, UUID> cases = new HashMap<>();
        try (var connection = TestEnvironment.connection(); var statement = connection.createStatement();
             var rows = statement.executeQuery("""
                     SELECT source.id, source.lemma, source.status AS submitted_status,
                            review.id AS case_id, review.reviewed_term, review.status AS case_status,
                            review.last_status_changed_by, review.type, review.matched_entry_id
                     FROM incubator.suggestions source
                     JOIN review.case_suggestions link ON link.source_suggestion_id = source.id
                     JOIN review.cases review ON review.id = link.case_id
                     """)) {
            while (rows.next()) {
                int sourceId = rows.getInt("id");
                assertEquals(rows.getString("lemma"), rows.getString("reviewed_term"),
                        "Submitted term was changed: " + sourceId);
                assertEquals(LEGACY_STATUSES.get(rows.getInt("submitted_status")), rows.getString("case_status"));
                assertNull(rows.getString("last_status_changed_by"));
                assertEquals("UNKNOWN", rows.getString("type"));
                assertNull(rows.getObject("matched_entry_id"));
                cases.put(sourceId, rows.getObject("case_id", UUID.class));
            }
        }
        assertEquals(expectedCount, cases.size());
        return cases;
    }

    private void waitForCases(int expectedCount) throws SQLException, InterruptedException {
        Instant deadline = Instant.now().plusSeconds(20);
        while (Instant.now().isBefore(deadline)) {
            try (var connection = TestEnvironment.connection(); var statement = connection.createStatement();
                 var rows = statement.executeQuery("SELECT count(*) FROM review.cases")) {
                rows.next();
                if (rows.getInt(1) == expectedCount) {
                    return;
                }
            }
            Thread.sleep(100);
        }
        fail("Expected " + expectedCount + " imported cases; see target/backend.log");
    }

    @After("@import")
    public void verifySourceDataWasNotChanged() throws SQLException {
        assertEquals(originalLegacyData, TestEnvironment.legacyDataSnapshot(),
                "The importer changed dict or incubator data");
    }
}
