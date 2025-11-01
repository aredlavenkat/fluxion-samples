package ai.fluxion.sample.streaming.kafka;

import ai.fluxion.connect.connectors.KafkaProducerSink;
import ai.fluxion.connect.streaming.sources.KafkaStreamingSource;
import ai.fluxion.core.engine.streaming.StreamingContext;
import ai.fluxion.core.engine.streaming.StreamingErrorPolicy;
import ai.fluxion.core.engine.streaming.StreamingPipelineExecutor;
import ai.fluxion.core.engine.streaming.StreamingRuntimeConfig;
import ai.fluxion.core.model.Document;
import ai.fluxion.core.model.Stage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

/**
 * Spins up a Kafka Testcontainer, streams messages through a Fluxion pipeline,
 * and verifies that the enriched payloads reach an output topic.
 */
public final class KafkaStreamingSample {

    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaStreamingSample.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private KafkaStreamingSample() {
    }

    public static void main(String[] args) throws Exception {
        DockerImageName image = DockerImageName.parse("confluentinc/cp-kafka:7.5.3");
        try (KafkaContainer kafka = new KafkaContainer(image)) {
            kafka.start();

            String bootstrap = kafka.getBootstrapServers();
            String inputTopic = "fluxion.sample.input";
            String outputTopic = "fluxion.sample.output";

            produceSampleMessages(bootstrap, inputTopic);
            LOGGER.info("Seeded sample messages into {}", inputTopic);

            Properties consumerProps = new Properties();
            consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
            consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "fluxion-sample" + UUID.randomUUID());
            consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

            KafkaStreamingSource source = new KafkaStreamingSource(consumerProps, inputTopic, Duration.ofMillis(500), 32);

            Properties producerProps = new Properties();
            producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
            producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

            KafkaProducerSink sink = new KafkaProducerSink(producerProps, outputTopic, 32, Duration.ofSeconds(5));

            StreamingRuntimeConfig runtimeConfig = StreamingRuntimeConfig.builder()
                    .directHandoff(false)
                    .queueCapacity(64)
                    .sourceQueueCapacity(16)
                    .workerThreadPoolSize(4)
                    .build();

            StreamingPipelineExecutor executor = new StreamingPipelineExecutor(32, runtimeConfig, StreamingErrorPolicy.failFast());
            StreamingContext context = new StreamingContext();

            List<Stage> stages = List.of(new Stage(Map.of("$set", Map.of("processed", true))));

            Thread worker = Thread.ofVirtual().start(() -> executor.processStream(source, stages, sink, context));

            try {
                List<Map<String, Object>> output = drainOutput(bootstrap, outputTopic, 2, Duration.ofSeconds(20));
                LOGGER.info("Received {} processed records", output.size());
                for (Map<String, Object> doc : output) {
                    LOGGER.info("Processed record: {}", doc);
                }
            } finally {
                source.cancel();
                sink.close();
                worker.join(Duration.ofSeconds(5));
            }
        }
    }

    private static void produceSampleMessages(String bootstrap, String topic) throws Exception {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            List<Map<String, Object>> payloads = List.of(
                    Map.of("orderId", "A-100", "total", 42, "tenant", "alpha"),
                    Map.of("orderId", "A-101", "total", 13, "tenant", "beta")
            );
            for (Map<String, Object> payload : payloads) {
                producer.send(new ProducerRecord<>(topic, payload.get("orderId").toString(), MAPPER.writeValueAsString(payload))).get();
            }
        }
    }

    private static List<Map<String, Object>> drainOutput(String bootstrap,
                                                          String topic,
                                                          int expected,
                                                          Duration timeout) throws Exception {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "fluxion-sample-consumer" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        List<Map<String, Object>> collected = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(Collections.singleton(topic));
            long deadline = System.nanoTime() + timeout.toNanos();
            while (System.nanoTime() < deadline && collected.size() < expected) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(250));
                records.forEach(record -> {
                    try {
                        Map<String, Object> decoded = MAPPER.readValue(record.value(), LinkedHashMap.class);
                        collected.add(decoded);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
            }
        }
        return collected;
    }
}
