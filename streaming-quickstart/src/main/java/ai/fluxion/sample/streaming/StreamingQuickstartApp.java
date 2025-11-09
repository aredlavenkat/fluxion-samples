package ai.fluxion.sample.streaming;

import ai.fluxion.core.engine.streaming.StreamingContext;
import ai.fluxion.core.engine.streaming.StreamingErrorPolicy;
import ai.fluxion.core.engine.streaming.StreamingPipelineExecutor;
import ai.fluxion.core.engine.streaming.StreamingRuntimeConfig;
import ai.fluxion.core.model.Document;
import ai.fluxion.core.model.Stage;
import ai.fluxion.core.util.DocumentParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Demonstrates the in-memory streaming executor by wiring an iterable source,
 * running aggregation stages, and logging emitted batches alongside stage metrics.
 */
public final class StreamingQuickstartApp {

    private static final Logger LOGGER = LoggerFactory.getLogger(StreamingQuickstartApp.class);

    public static void main(String[] args) throws Exception {
        List<Document> events = DocumentParser.getDocumentsFromJsonArray("""
                [
                  {"orderId": "A-100", "status": "PAID", "total": 120.0},
                  {"orderId": "A-101", "status": "PENDING", "total": 65.0},
                  {"orderId": "A-102", "status": "PAID", "total": 98.5},
                  {"orderId": "A-103", "status": "PAID", "total": 210.0}
                ]
                """);

        List<Stage> stages = DocumentParser.getStagesFromJsonArray("""
                [
                  {"$match": {"status": "PAID"}},
                  {"$group": {
                      "_id": "$status",
                      "count": {"$sum": 1},
                      "totalRevenue": {"$sum": "$total"}
                  }}
                ]
                """);

        StreamingRuntimeConfig config = StreamingRuntimeConfig.builder()
                .microBatchSize(2)
                .queueCapacity(64)
                .sourceQueueCapacity(4)
                .directHandoff(true)
                .build();

        StreamingErrorPolicy errorPolicy = StreamingErrorPolicy.builder()
                .maxRetries(2)
                .build();

        StreamingPipelineExecutor executor = new StreamingPipelineExecutor(64, config, errorPolicy);
        StreamingContext context = new StreamingContext(null, Map.of("tenantId", "demo"), "orders-stream");

        executor.processStream(events, stages, documents -> {
            for (Document doc : documents) {
                LOGGER.info("Streaming output: {}", doc);
            }
        }, context);

        LOGGER.info("Stage metrics: {}", context.metrics().snapshot());
    }
}
