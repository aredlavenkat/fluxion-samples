package ai.fluxion.sample.core;

import ai.fluxion.core.engine.PipelineExecutor;
import ai.fluxion.core.model.Document;
import ai.fluxion.core.model.Stage;
import ai.fluxion.core.util.DocumentParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Minimal command-line sample for the core pipeline engine. It loads a JSON
 * pipeline definition, executes it against an in-memory document list, and
 * logs the transformed documents.
 */
public final class CoreQuickstartApp {

    private static final Logger LOGGER = LoggerFactory.getLogger(CoreQuickstartApp.class);

    public static void main(String[] args) throws Exception {
        PipelineExecutor executor = new PipelineExecutor();

        List<Document> input = DocumentParser.getDocumentsFromJsonArray("""
                [
                  {"orderId": "A-100", "status": "PAID", "total": 120.0},
                  {"orderId": "A-101", "status": "PENDING", "total": 65.0},
                  {"orderId": "A-102", "status": "PAID", "total": 98.5}
                ]
                """);

        List<Stage> stages = DocumentParser.getStagesFromJsonArray(readResource("/pipelines/orders.json"));

        List<Document> results = executor.run(input, stages, Map.of("tenantId", "demo-store"));
        results.forEach(document -> LOGGER.info("Result: {}", document));
    }

    private static String readResource(String path) throws IOException {
        try (InputStream inputStream = CoreQuickstartApp.class.getResourceAsStream(path)) {
            if (inputStream == null) {
                throw new IOException("Missing resource: " + path);
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
