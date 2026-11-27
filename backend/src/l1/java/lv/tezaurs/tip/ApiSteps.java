package lv.tezaurs.tip.l1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.cucumber.java.After;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public class ApiSteps {
    private static final String AUTHORIZATION = "Basic " + Base64.getEncoder()
            .encodeToString("redaktors:test".getBytes(StandardCharsets.ISO_8859_1));

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final JsonMapper json = JsonMapper.builder().build();
    @LocalServerPort
    private int port;

    private int status;
    private JsonNode body;
    private String tezaursSnapshot;

    @After
    public void assertTezaursDataUnchanged() throws SQLException {
        try {
            if (tezaursSnapshot != null) {
                assertEquals(tezaursSnapshot, TestEnvironment.snapshotTezaursData(),
                        "The API changed read-only Tēzaurs data");
            }
        } finally {
            client.close();
        }
    }

    @When("I GET {string}")
    public void sendGetRequest(String path) throws SQLException {
        sendRequest("GET", path, null);
    }

    @When("I POST {string} with:")
    public void sendPostRequest(String path, String requestBody) throws SQLException {
        sendRequest("POST", path, requestBody);
    }

    @When("I PUT {string} with:")
    public void sendPutRequest(String path, String requestBody) throws SQLException {
        sendRequest("PUT", path, requestBody);
    }

    @When("I DELETE {string}")
    public void sendDeleteRequest(String path) throws SQLException {
        sendRequest("DELETE", path, null);
    }

    @Then("the response status is {int}")
    public void assertResponseStatus(int expected) {
        assertEquals(expected, status, String.valueOf(body));
    }

    @Then("the response contains:")
    public void assertResponseContains(String expected) {
        assertJsonContains(json.readTree(expected), body, "");
    }

    @Then("Tēzaurs sense {int} is a subsense of Tēzaurs sense {int} in the draft")
    public void assertExistingSubsenseParent(int childId, int parentId) {
        JsonNode child = findExistingSenseInResponse(childId);
        JsonNode parent = findExistingSenseInResponse(parentId);
        assertTrue(parent.path("id").isString(), "The parent draft sense must have an ID");
        assertEquals(parent.get("id"), child.get("parentSenseId"));
    }

    private JsonNode findExistingSenseInResponse(int existingSenseId) {
        for (JsonNode sense : body) {
            if (sense.path("existingSenseId").asInt(-1) == existingSenseId) {
                return sense;
            }
        }
        throw new AssertionError("No draft for Tēzaurs sense " + existingSenseId + ": " + body);
    }

    private void assertJsonContains(JsonNode expected, JsonNode actual, String path) {
        String where = path.isEmpty() ? "/" : path;
        if (expected.isObject()) {
            assertTrue(actual.isObject(), where + " must be an object: " + body);
            for (Map.Entry<String, JsonNode> field : expected.properties()) {
                assertTrue(actual.has(field.getKey()), where + " has no field " + field.getKey() + ": " + body);
                assertJsonContains(field.getValue(), actual.get(field.getKey()), path + "/" + field.getKey());
            }
        } else if (expected.isArray()) {
            assertTrue(actual.isArray(), where + " must be an array: " + body);
            assertEquals(expected.size(), actual.size(), where + " length: " + body);
            for (int index = 0; index < expected.size(); index++) {
                assertJsonContains(expected.get(index), actual.get(index), path + "/" + index);
            }
        } else {
            assertEquals(expected, actual, where);
        }
    }

    private void sendRequest(String method, String path, String requestBody) throws SQLException {
        DataSteps.insertPendingFixtureRows();
        if (tezaursSnapshot == null) {
            tezaursSnapshot = TestEnvironment.snapshotTezaursData();
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("Authorization", AUTHORIZATION)
                .method(method, requestBody == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(requestBody))
                .build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            status = response.statusCode();
            body = json.readTree(response.body().isBlank() ? "null" : response.body());
        } catch (IOException exception) {
            throw new AssertionError("Cannot reach the test backend", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }
}
