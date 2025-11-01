package ai.fluxion.sample.enrich;

import ai.fluxion.core.engine.PipelineExecutor;
import ai.fluxion.core.model.Document;
import ai.fluxion.core.model.Stage;
import ai.fluxion.core.util.DocumentParser;
import ai.fluxion.enrich.evaluator.operators.linked.services.HttpCallOperator;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EnrichHttpSampleTest {

    private MockWebServer server;

    @BeforeEach
    void setUp() throws IOException {
        HttpCallOperator.resetResilience();
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void enrichesCustomerProfileViaHttpCall() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"customerId":"C-1","tier":"GOLD","lifetimeValue":12890.5}
                        """));

        List<Document> input = DocumentParser.getDocumentsFromJsonArray("""
                [
                  {"customerId": "C-1", "status": "ACTIVE"}
                ]
                """);

        String baseUrl = server.url("/").toString();
        List<Stage> stages = DocumentParser.getStagesFromJsonArray("""
                [
                  {"$addFields": {
                      "profile": {"$httpCall": {
                          "url": "%sapi/customers/{customerId}",
                          "method": "GET",
                          "params": {"customerId": "$customerId"},
                          "connectTimeoutMs": 1000,
                          "readTimeoutMs": 1000
                      }}
                  }},
                  {"$project": {"_id": 0, "customerId": 1, "status": 1, "profile": 1}}
                ]
                """.formatted(baseUrl));

        PipelineExecutor executor = new PipelineExecutor();
        List<Document> results = executor.run(input, stages, Map.of("traceId", "sample"));

        assertEquals(1, results.size());
        Document enriched = results.get(0);
        assertEquals("C-1", enriched.get("customerId"));
        assertEquals("ACTIVE", enriched.get("status"));

        @SuppressWarnings("unchecked")
        Map<String, Object> profile = (Map<String, Object>) enriched.get("profile");
        assertEquals("GOLD", profile.get("tier"));
    }
}
