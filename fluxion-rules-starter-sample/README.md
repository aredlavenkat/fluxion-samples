# Fluxion Rules Starter Sample

This Spring Boot sample demonstrates how to consume the `fluxion-rules-spring-boot-starter`, expose the auto-configured controller endpoints, and surface rule metadata via Actuator.

## Prerequisites

Install the Fluxion artifacts locally so the sample can resolve the snapshot starter:

```bash
mvn -pl fluxion-core,fluxion-rules install -Dmaven.test.skip=true
mvn -N install
```

## Run

```bash
cd samples/fluxion-rules-starter-sample
mvn spring-boot:run
```

## Try it

- Evaluate a rule set:

  ```bash
  curl -X POST http://localhost:8080/rules/orders/evaluate \
       -H 'Content-Type: application/json' \
       -d '{"total":1500,"customer":{"status":"new"}}'
  ```

  Sample response:

  ```json
  {
    "ruleSetId": "orders",
    "passes": [
      {
        "id": "high-value",
        "name": "High value order",
        "salience": 10,
        "attributes": {
          "flagged": true
        }
      },
      {
        "id": "new-customer",
        "name": "New customer order",
        "salience": 5,
        "attributes": {
          "flagged": true
        }
      }
    ]
  }
  ```

- Swap `orders` for `customers` or `returns` to exercise the additional rule sets shipped with the sample.

- Inspect rule metadata via Actuator:

  ```bash
  curl http://localhost:8080/actuator/fluxionRules
  ```

- Health indicator:

  ```bash
  curl http://localhost:8080/actuator/health/fluxionRules
  ```

The sample also demonstrates listening for `RuleSetsLoadedEvent` to register a custom action (`flag-order`) on startup.
