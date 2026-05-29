# LenseIQ Grafana dashboards

Two importable Grafana dashboards that wire LenseIQ's hot-topics view and executive cluster-health rollup to your existing Prometheus / Confluent JMX exporter scrape.

| File | Dashboard title | UID |
|---|---|---|
| `kafka-hot-topics.json` | LenseIQ — Kafka Hot Topics | `lenseiq-hot-topics` |
| `kafka-executive-overview.json` | LenseIQ — Executive Overview | `lenseiq-exec-overview` |

The Cluster column in the executive dashboard links into the Hot Topics dashboard with the cluster pre-selected, so the demo flow is **Exec view → click cluster → Hot Topics**.

## Import

For the local LenseIQ Docker stack, the dashboards and Prometheus datasource are provisioned automatically:

```bash
docker compose up -d kafka prometheus grafana
```

Open Grafana at `http://localhost:3000` and sign in with `admin` / `admin`. The dashboards appear under the `LenseIQ` folder. The local Prometheus scrape labels the single Kafka broker as `env="PROD"` and `job="LOCAL_lenseiq-kafka"` so the default dashboard variables show data immediately.

Manual import into another Grafana instance:

1. Grafana → Dashboards → New → Import.
2. Upload the JSON file or paste its contents.
3. When prompted, select your Prometheus datasource (the variable is named `DS_PROMETHEUS`).
4. Save.

Repeat for the second JSON.

## Variables

### Hot Topics dashboard

| Variable | Source | Default | Notes |
|---|---|---|---|
| `env` | custom list | `PROD` | Add UAT/DEV/etc. as needed |
| `tenant` | `label_values(..., job)` with regex `/^([^_]+)_/` | All | First underscore segment of `job` (CHANNELS, CSF, DDA, EXPRESS, …) |
| `cluster` | `label_values(...{job=~"(${tenant:pipe})_.*"}, job)` | All | Filtered by selected tenant(s) |
| `topic` | `label_values(...{job=~"$cluster"}, topic)` | All | Used by the bottom drill-down panel |
| `top_n` | custom `5,10,20,50` | 5 | Drives the Top N bar gauges |
| `min_bytes_per_sec` | textbox | `1048576` (1 MB/s) | Threshold for "hot" headline count |

### Executive Overview dashboard

| Variable | Source | Default |
|---|---|---|
| `env` | custom list | `PROD` |

## Time range

Both dashboards default to **now-7d → now**. The time picker exposes 1h, 6h, 12h, 24h, 2d, 7d, 14d, 30d — pick 30d to see the full retention window for trending.

## Metric names assumed (Confluent JMX exporter, lowercased)

If your exporter uses different names, do a global find-and-replace on the JSON before import. Mapping:

| Used in dashboard | What it represents |
|---|---|
| `kafka_server_brokertopicmetrics_bytesinpersec_oneminuterate` | Per-topic bytes-in 1m rate |
| `kafka_server_brokertopicmetrics_bytesoutpersec_oneminuterate` | Per-topic bytes-out 1m rate |
| `kafka_server_brokertopicmetrics_messagesinpersec_oneminuterate` | Per-topic messages-in 1m rate |
| `kafka_server_brokertopicmetrics_totalproducerequestspersec_oneminuterate` | Per-topic produce request rate |
| `kafka_server_brokertopicmetrics_totalfetchrequestspersec_oneminuterate` | Per-topic fetch request rate |
| `kafka_log_log_size` | Per-(topic,partition) log size on disk |
| `kafka_controller_kafkacontroller_activecontrollercount` | Active controllers per cluster (should be 1) |
| `kafka_controller_kafkacontroller_offlinepartitionscount` | Offline partitions per cluster |
| `kafka_server_replicamanager_underreplicatedpartitions` | Under-replicated partitions per cluster |

If your team uses recording-rule aliases (`bytes_in_per_sec`, etc.), rename the expressions in `targets[].expr`.

If your metrics are camel-cased (`kafka_server_BrokerTopicMetrics_BytesInPerSec_OneMinuteRate`), use sed:

```bash
sed -i '' \
  -e 's/kafka_server_brokertopicmetrics_bytesinpersec_oneminuterate/kafka_server_BrokerTopicMetrics_BytesInPerSec_OneMinuteRate/g' \
  -e 's/kafka_server_brokertopicmetrics_bytesoutpersec_oneminuterate/kafka_server_BrokerTopicMetrics_BytesOutPerSec_OneMinuteRate/g' \
  -e 's/kafka_server_brokertopicmetrics_messagesinpersec_oneminuterate/kafka_server_BrokerTopicMetrics_MessagesInPerSec_OneMinuteRate/g' \
  kafka-hot-topics.json kafka-executive-overview.json
```

## Hot Topics dashboard — what each panel does

| Row | Panel | Query summary |
|---|---|---|
| 1 | Hot topics count | `count( topk(...) > $min_bytes_per_sec )` |
| 1 | Total topics | `count( count by (topic) (...) )` |
| 1 | Top topic + throughput | `topk(1, sum by (topic) (...))` |
| 2 | Top N by bytes in+out | `topk($top_n, sum by (topic) (in + out))` |
| 2 | Top N by messages | `topk($top_n, sum by (topic) (messages))` |
| 3 | Top N by log size | `topk($top_n, sum by (topic) (log_size))` |
| 3 | Top N by request rate | `topk($top_n, sum by (topic) (produce + fetch))` |
| 4 | Top 5 trend over $__range | `topk(5, sum by (topic) (...))` plotted as a multi-line time series — set time range to 30d for the full retention window |
| 5 | Trending up table | `(avg_over_time[24h] / avg_over_time[7d]) - 1`, sorted descending, color-coded (green ≤10%, orange ≥30%, red ≥100%) |
| 5 | Bytes-in share by tenant | Donut, `sum by (tenant)` after `label_replace(..., "tenant", "$1", "job", "^([^_]+)_.*")` |
| 6 | Drill-down on $topic | Bytes in / bytes out / messages in for the selected topic, full time range |

## Executive Overview dashboard — what each panel does

| Row | Panel | Query summary |
|---|---|---|
| 1 | Total clusters | `count( count by (job) (active_controller_count) )` |
| 1 | Healthy | offline=0 AND under_replicated=0 AND active_controllers=1 |
| 1 | Degraded | under_replicated > 0 AND offline = 0 |
| 1 | Down | offline > 0 OR active_controllers ≠ 1 |
| 2 | Estate health history (7d) | Stacked bar of healthy / degraded / down counts over time |
| 3 | Cluster status — current | One row per cluster with URP, offline, controllers, bytes in/out, topic count. Cluster name links to Hot Topics dashboard with cluster pre-selected. |

## Demo flow suggestion

1. Open **LenseIQ — Executive Overview**, time range 7d. Stat row tells the leadership story in 5 seconds: "37 clusters, 34 healthy, 2 degraded, 1 down."
2. Scroll to the cluster table. Sort by Offline desc. Worst cluster is on top.
3. Click that cluster name → jumps to **LenseIQ — Kafka Hot Topics** with the cluster pre-selected.
4. Time range 30d. Show the trending-up table — call out a topic whose 24h is 80% above its 7d baseline.
5. Pick that topic in the `$topic` variable — the bottom drill-down panel renders the 30-day per-metric trend.

## Known follow-ups (phase 2)

- Per-partition drill-down (currently parked — JMX `BrokerTopicMetrics` is topic-level only; per-partition rates would require an AdminClient end-offset poller landing the deltas as a Prometheus exposition).
- Tenant SLO panels (e.g. URP minutes per tenant per month) — needs an SLO definition first.
- Annotation overlays for cluster restarts / rebalances — wire `kafka_server_KafkaServer_BrokerState` transitions as Grafana annotations.
