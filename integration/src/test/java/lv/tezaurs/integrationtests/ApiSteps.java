package lv.tezaurs.integrationtests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public class ApiSteps {
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();
    private final JsonMapper json = JsonMapper.builder().build();
    private final Map<String, String> savedValues = new HashMap<>();
    private HttpResponse<String> response;
    private JsonNode responseBody;
    private String originalLegacyData;

    @Before
    public void resetScenario() throws Exception {
        TestEnvironment.resetData();
        originalLegacyData = TestEnvironment.legacyDataSnapshot();
    }

    @When("I request {string}")
    public void request(String path) throws InterruptedException {
        send("GET", path, null);
    }

    @When("I POST {string} with:")
    public void post(String path, String body) throws InterruptedException {
        send("POST", path, body);
    }

    @When("I PUT {string} with:")
    public void put(String path, String body) throws InterruptedException {
        send("PUT", path, body);
    }

    @When("I DELETE {string}")
    public void delete(String path) throws InterruptedException {
        send("DELETE", path, null);
    }

    @Then("the response status is {int}")
    public void responseStatusIs(int expectedStatus) {
        assertEquals(expectedStatus, response.statusCode(), response.body());
    }

    @Then("I save {string} as {string}")
    public void saveValue(String pointer, String name) {
        JsonNode value = responseBody.at(pointer);
        assertFalse(value.isMissingNode() || value.isNull(), "Missing response value: " + pointer);
        savedValues.put(name, value.asString());
    }

    @Then("the response field {string} equals {string}")
    public void responseFieldEquals(String pointer, String expected) {
        assertEquals(expand(expected), responseBody.at(pointer).asString(), pointer);
    }

    @Then("the response field {string} is {int}")
    public void responseFieldIsNumber(String pointer, int expected) {
        assertTrue(responseBody.at(pointer).isIntegralNumber(), pointer + " must be an integer");
        assertEquals(expected, responseBody.at(pointer).asInt(), pointer);
    }

    @Then("the response field {string} is null")
    public void responseFieldIsNull(String pointer) {
        assertTrue(responseBody.at(pointer).isNull(), pointer + " must be null");
    }

    @Then("the response field {string} contains {int} items")
    public void responseArrayHasSize(String pointer, int expected) {
        assertTrue(responseBody.at(pointer).isArray(), pointer + " must be an array");
        assertEquals(expected, responseBody.at(pointer).size(), pointer);
    }

    @Then("the service reports that it is healthy")
    public void serviceIsHealthy() {
        assertEquals("UP", responseBody.path("status").asString());
    }

    @Then("the response is page {int} with a page size of {int}")
    public void responseIsPage(int expectedPage, int pageSize) {
        assertTrue(responseBody.path("items").isArray(), "items must be an array");
        assertTrue(responseBody.path("page").isIntegralNumber(), "page must be an integer");
        assertTrue(responseBody.path("totalItems").isIntegralNumber(), "totalItems must be an integer");
        assertTrue(responseBody.path("totalPages").isIntegralNumber(), "totalPages must be an integer");
        assertEquals(expectedPage, responseBody.path("page").asInt());
        long totalItems = responseBody.path("totalItems").asLong();
        long totalPages = responseBody.path("totalPages").asLong();
        int returnedItems = responseBody.path("items").size();
        assertTrue(totalItems >= 0, "totalItems cannot be negative");
        assertTrue(returnedItems <= pageSize, "The page exceeds the requested size");
        assertTrue(totalItems >= returnedItems, "The total cannot be smaller than this page");
        long expectedTotalPages = (totalItems + pageSize - 1) / pageSize;
        assertEquals(expectedTotalPages, totalPages);
    }

    @Then("every returned case has status {string}")
    public void casesHaveStatus(String expectedStatus) {
        for (JsonNode review : responseBody.path("items")) {
            assertEquals(expectedStatus, review.path("status").asString());
        }
    }

    @Then("each returned case's suggestion count matches its details")
    public void suggestionCountsMatchDetails() throws InterruptedException {
        JsonNode reviews = responseBody.path("items");
        for (JsonNode review : reviews) {
            String reviewId = review.path("id").asString();
            request("/api/reviews/" + reviewId);
            responseStatusIs(200);
            assertTrue(responseBody.path("sources").isArray(), "Case sources must be an array");
            assertTrue(review.path("sourceCount").isIntegralNumber(), "sourceCount must be an integer");
            assertEquals(responseBody.path("sources").size(), review.path("sourceCount").asLong(),
                    "Suggestion count for case " + reviewId);
        }
    }

    @Given("a review case with status {string}")
    public void caseWithStatus(String status) throws InterruptedException {
        String initialStatus = "IN_PROGRESS";
        if (status.equals("READY_FOR_REVIEW") || status.equals("GARBAGE")) {
            initialStatus = status;
        }
        post("/api/suggestions/3/status", json.writeValueAsString(Map.of("status", initialStatus)));
        responseStatusIs(200);
        saveValue("/id", "caseId");
        if (status.equals("ALREADY_EXISTS")) {
            post("/api/reviews/{caseId}/tezaurs-check",
                    "{\"status\":\"MEANING_ALREADY_EXISTS\",\"matchedEntryId\":100}");
        } else if (status.equals("COMPLETED")) {
            post("/api/reviews/{caseId}/tezaurs-check",
                    "{\"status\":\"MEANING_NOT_FOUND\",\"matchedEntryId\":100}");
            responseStatusIs(200);
            put("/api/reviews/{caseId}/sense-drafts", "{\"senses\":[{\"gloss\":\"Jauna nozīme\",\"flags\":{}}]}");
            responseStatusIs(200);
            post("/api/reviews/{caseId}/status", "{\"status\":\"COMPLETED\"}");
        } else if (!status.equals(initialStatus)) {
            post("/api/reviews/{caseId}/status", json.writeValueAsString(Map.of("status", status)));
        }
        responseStatusIs(200);
    }

    @Given("a case combines suggestions {string}")
    public void combinedSuggestions(String ids) throws InterruptedException {
        String[] suggestionIds = ids.split(",");
        post("/api/suggestions/" + suggestionIds[0].trim() + "/status", "{\"status\":\"IN_PROGRESS\"}");
        responseStatusIs(200);
        saveValue("/id", "caseId");
        for (int index = 1; index < suggestionIds.length; index++) {
            post("/api/suggestions/" + suggestionIds[index].trim() + "/duplicate", "{\"reviewId\":\"{caseId}\"}");
            responseStatusIs(200);
        }
    }

    private void send(String method, String path, String body) throws InterruptedException {
        HttpRequest.BodyPublisher content = HttpRequest.BodyPublishers.noBody();
        if (body != null) {
            content = HttpRequest.BodyPublishers.ofString(expand(body));
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(TestEnvironment.baseUrl() + expand(path)))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .method(method, content).build();
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException exception) {
            throw new AssertionError("Cannot reach the test backend. See target/backend.log", exception);
        }
        String responseText = response.body();
        if (responseText.isBlank()) {
            responseText = "null";
        }
        responseBody = json.readTree(responseText);
    }

    private String expand(String text) {
        for (Map.Entry<String, String> value : savedValues.entrySet()) {
            text = text.replace("{" + value.getKey() + "}", value.getValue());
        }
        return text;
    }

    @After
    public void verifyLegacyDataAndCloseClient() throws Exception {
        try {
            if (originalLegacyData != null) {
                assertEquals(originalLegacyData, TestEnvironment.legacyDataSnapshot(),
                        "The API changed dict or incubator data");
            }
        } finally {
            client.close();
        }
    }
}
