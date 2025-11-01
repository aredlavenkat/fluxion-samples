# Fluxion Samples

Companion repository containing runnable examples for the Fluxion platform.
Each module highlights a different part of the stack and depends on the
published `0.0.1-SNAPSHOT` artifacts from `fluxion-core-engine-java`.

## Modules

- `core-quickstart` – standalone Java example that parses a pipeline JSON file
  and executes it with `PipelineExecutor`.
- `streaming-quickstart` – demonstrates the streaming runtime with the in-memory
  iterable source and collecting sink.
- `connectors-catalog` – introspects the connector registry and prints the
  available source/sink providers at runtime.
- `enrich-http` – JUnit sample using MockWebServer to showcase `$httpCall`
  orchestration with retry-friendly configuration.
- `fluxion-rules-starter-sample` – Spring Boot service that loads rules
  via the starter, exposes a REST endpoint, and reuses the auto-configured
  evaluation service.
- `workflow-quickstart` – Temporal TestWorkflowEnvironment demo showing how to
  register Fluxion activities/workflows and execute a rule evaluation.

## Getting Started

1. Install the platform artifacts locally from the core repository:
   ```bash
   cd ../fluxion-core-engine-java
   mvn -DskipTests install
   ```
2. Return to this directory and build all samples:
   ```bash
   mvn clean package
   ```
3. Run an individual sample (examples below):
   ```bash
   # Core pipeline CLI
   mvn -pl core-quickstart exec:java

   # Streaming demo
   mvn -pl streaming-quickstart exec:java

  # Spring Boot rules service
  mvn -pl fluxion-rules-starter-sample spring-boot:run

  # Temporal workflow demo
  mvn -pl workflow-quickstart exec:java
   ```

## Repository Layout

This repository is intentionally self-contained. No generated code is checked
in; run `mvn clean` to wipe build outputs. Additional samples can be added by
creating a new Maven module and appending it to the root `pom.xml`.
