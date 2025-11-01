package ai.fluxion.sample.streaming.mongo;

import ai.fluxion.core.engine.connectors.ConnectorConfig;
import ai.fluxion.core.engine.connectors.ConnectorFactory;
import ai.fluxion.core.engine.connectors.SourceConnectorConfig;
import ai.fluxion.core.engine.connectors.SourceConnectorContext;
import ai.fluxion.core.engine.streaming.StreamingContext;
import ai.fluxion.core.engine.streaming.StreamingErrorPolicy;
import ai.fluxion.core.engine.streaming.StreamingPipelineExecutor;
import ai.fluxion.core.engine.streaming.StreamingRuntimeConfig;
import ai.fluxion.core.engine.streaming.StreamingSink;
import ai.fluxion.core.engine.streaming.StreamingSource;
import ai.fluxion.core.model.Stage;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Starts a MongoDB Testcontainer and streams change events from one collection
 * into another, mirroring the change-stream E2E test.
 */
public final class MongoStreamingSample {

    private static final Logger LOGGER = LoggerFactory.getLogger(MongoStreamingSample.class);

    private MongoStreamingSample() {
    }

    public static void main(String[] args) throws Exception {
        DockerImageName image = DockerImageName.parse("mongo:7.0");
        try (MongoDBContainer mongo = new MongoDBContainer(image).withCommand("--replSet", "rs0", "--bind_ip_all")) {
            mongo.start();
            mongo.execInContainer("mongosh", "--quiet", "--eval",
                    "rs.initiate({_id:'rs0', members:[{_id:0, host:'127.0.0.1:27017'}]})");

            String replicaSetUri = mongo.getReplicaSetUrl();
            String database = "fluxion";
            String sourceCollection = "input_orders";
            String sinkCollection = "output_orders";

            try (MongoClient client = MongoClients.create(replicaSetUri)) {
                MongoCollection<Document> src = client.getDatabase(database).getCollection(sourceCollection);
                MongoCollection<Document> dst = client.getDatabase(database).getCollection(sinkCollection);
                src.drop();
                dst.drop();
            }

            SourceConnectorConfig sourceConfig = SourceConnectorConfig.builder("mongodb")
                    .option("connectionString", replicaSetUri)
                    .option("database", database)
                    .option("collection", sourceCollection)
                    .option("queueCapacity", 32)
                    .option("maxBatchSize", 64)
                    .option("fullDocument", "update_lookup")
                    .build();

            ConnectorConfig sinkConfig = ConnectorConfig.builder("mongodb", ConnectorConfig.Kind.SINK)
                    .option("connectionString", replicaSetUri)
                    .option("database", database)
                    .option("collection", sinkCollection)
                    .option("mode", "upsert")
                    .option("keyField", "_id")
                    .build();

            StreamingRuntimeConfig runtimeConfig = StreamingRuntimeConfig.builder()
                    .directHandoff(false)
                    .queueCapacity(64)
                    .workerThreadPoolSize(4)
                    .build();

            StreamingContext context = new StreamingContext();
            StreamingSource source = ConnectorFactory.createSource(sourceConfig, SourceConnectorContext.from(context));
            StreamingSink sink = ConnectorFactory.createSink(sinkConfig);

            StreamingPipelineExecutor executor = new StreamingPipelineExecutor(64, runtimeConfig, StreamingErrorPolicy.failFast());
            List<Stage> stages = List.of(new Stage(Map.of("$set", Map.of("status", "processed"))));

            Thread worker = Thread.ofVirtual().start(() -> executor.processStream(source, stages, sink, context));

            try (MongoClient client = MongoClients.create(replicaSetUri)) {
                MongoCollection<Document> src = client.getDatabase(database).getCollection(sourceCollection);
                src.insertMany(List.of(
                        new Document("_id", "order-1").append("amount", 42).append("tenant", "alpha"),
                        new Document("_id", "order-2").append("amount", 17).append("tenant", "beta")
                ));

                MongoCollection<Document> dst = client.getDatabase(database).getCollection(sinkCollection);
                long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
                while (System.nanoTime() < deadline && dst.countDocuments() < 2) {
                    Thread.sleep(200);
                }

                dst.find().forEach(doc -> LOGGER.info("Processed document: {}", doc.toJson()));
            } finally {
                source.cancel();
                sink.close();
                worker.join(Duration.ofSeconds(5));
            }
        }
    }
}
