# AdServe

A small, privacy-preserving ad decision service in **Java 17 and Spring Boot 3**, modeled on the shape of an ad platform: a request comes in, an auction picks one ad in microseconds, spend is charged against a campaign budget, and impression and click events stream through **Kafka** to keep click-rate predictions fresh.

It is a learning and portfolio project. It is not deployed, has no real traffic, and keeps campaigns in memory.

## What it does

| Piece | What it does |
|---|---|
| `POST /v1/ads/decide` | Takes `{placement, locale?, category?}` and returns the winning ad and the price charged, or `204` when nothing eligible has budget. |
| Auction | Second-price auction on expected revenue (bid x predicted click rate). The winner pays the smallest price that still beats the runner-up, capped at its own bid. |
| Budget ledger | Lock-free compare-and-set spend reservation, so concurrent requests can never overspend a campaign. If the winner is out of budget the next bidder is tried. |
| Click-rate model | Smoothed click-through rate per campaign, learned from events. A new campaign starts at a 5% prior. |
| `POST /v1/events` | Records an IMPRESSION or CLICK. Published to a Kafka topic keyed by campaign id and consumed to update the click-rate model (or applied directly when Kafka is off, the default). |
| `GET /v1/campaigns/{id}/report` | Aggregate-only counts and remaining budget. |
| `/actuator/prometheus` | Micrometer metrics, including an `adserve.decision.latency` timer with p50, p95, p99. |

## Privacy by design

- Targeting is **contextual only**: placement, locale, category. No user id, device id, or IP address exists anywhere in the model.
- The API rejects unknown JSON fields with `400`, so a client cannot smuggle a user identifier in (there is a test for this).
- Events and reports are aggregate-only: campaign, placement, type.

## Run it

```sh
mvn test                 # 18 tests, including a real in-JVM Kafka broker
mvn spring-boot:run      # starts on :8080 with three demo campaigns
curl -s -X POST localhost:8080/v1/ads/decide -H 'Content-Type: application/json' \
  -d '{"placement":"app-store-search","locale":"en-US","category":"games"}'
```

To use a real Kafka cluster: `--adserve.kafka.enabled=true --spring.kafka.bootstrap-servers=host:9092`.

## Tests (JUnit 5)

- `AuctionServiceTest` (8): highest eCPM wins, second-price charging, click rate beating a higher bid, placement/locale/category targeting, budget fall-through, no-ad case, budget exhaustion.
- `BudgetLedgerConcurrencyTest` (2): 64 threads make 6,400 one-cent spend attempts against a 1,000-cent budget, and exactly 1,000 succeed.
- `AdControllerHttpTest` (6): full HTTP round trips on a random port, including the user-id rejection and event-to-report flow.
- `KafkaEventFlowTest` (1): publishes 20 impressions and 10 clicks through an embedded Kafka broker and waits for the consumer to move the click rate to exactly 0.275.
- `LatencyBenchmarkTest` (1): times the auction alone.

## Measured latency (in-process, auction only)

500 matching campaigns, 8 threads, 200,000 decisions after warm-up, on a shared cloud sandbox:
**p50 about 33 microseconds, p99 between 1.1 and 2.5 milliseconds** across 4 runs. This excludes HTTP, JSON, and the network. It is a sanity check on the algorithm, not a production benchmark.

## Honest limits

- Campaigns, budgets, and click-rate counters live in memory; a restart resets them. There is no database.
- Spend is reserved at decision time rather than at impression time.
- Kafka was tested against Spring's embedded broker, not a multi-broker cluster.
- No authentication, no multi-region, no frequency capping.
