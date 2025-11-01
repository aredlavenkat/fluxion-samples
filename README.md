# Fluxion Samples

Companion repository containing runnable examples for the Fluxion platform.
Each module highlights a different part of the stack and depends on the
published `0.0.1-SNAPSHOT` artifacts from `fluxion-core-engine-java`.

## Modules

- `core-quickstart` – standalone Java example that parses a pipeline JSON file
  and executes it with `PipelineExecutor`.
- `streaming-quickstart` – demonstrates the streaming runtime with the in-memory
  iterable source and collecting sink.
- `streaming-kafka` – spins up a Kafka Testcontainer and streams messages from
  an input topic to an output topic via Fluxion stages.
- `streaming-mongo` – mirrors the MongoDB change-stream E2E test using a
  Testcontainer to fan documents into a sink collection.
- `enrich-http` – JUnit sample using MockWebServer to showcase `$httpCall`
  orchestration with retry-friendly configuration.
- `fluxion-rules-starter-sample` – Spring Boot service that loads rules
  via the starter, exposes a REST endpoint, and reuses the auto-configured
  evaluation service.
- `workflow-quickstart` – Temporal TestWorkflowEnvironment demo that evaluates
  order-approval rules. It exercises both the automatic approval path and the
  manual-review branch (human involvement simulated via an activity stub).

## Getting Started

1. Install the platform artifacts locally from the core repository:
   ```bash
   cd ../fluxion-core-engine-java
   mvn -DskipTests install
   ```
2. Change into the sample you want to explore and run it directly. Each module is
   independent; no need to build the entire suite at once.
   ```bash
   # Core pipeline CLI
   cd ../fluxion-sample/core-quickstart
   mvn exec:java

   # Streaming demo (in-memory source/sink)
   cd ../fluxion-sample/streaming-quickstart
   mvn exec:java

   # Streaming demo (Kafka connector, requires Docker)
   cd ../fluxion-sample/streaming-kafka
   mvn exec:java

   # Streaming demo (MongoDB change stream, requires Docker)
   cd ../fluxion-sample/streaming-mongo
   mvn exec:java

   # Spring Boot rules service
   cd ../fluxion-sample/fluxion-rules-starter-sample
   mvn spring-boot:run

   # Temporal workflow demo
   cd ../fluxion-sample/workflow-quickstart
   mvn exec:java
   ```

   The workflow sample prints both scenarios:

   - LOW VALUE order → approved automatically (`flag=auto`).
   - HIGH VALUE order → routed to manual review and rejected (`flag=review`, `manualApproval=false`).

## Repository Layout

This repository is intentionally self-contained. No generated code is checked
in; run `mvn clean` to wipe build outputs. Additional samples can be added by
creating a new Maven module and appending it to the root `pom.xml`.
