# LenseIQ Grafana / Prometheus Export Bundle

This bundle contains the files needed to import the LenseIQ Kafka dashboards into another Grafana and Prometheus environment.

## Contents

- `dashboards/kafka-hot-topics.json`
  - Grafana dashboard: `LenseIQ - Kafka Hot Topics`
  - UID: `lenseiq-hot-topics`
- `dashboards/kafka-executive-overview.json`
  - Grafana dashboard: `LenseIQ - Executive Overview`
  - UID: `lenseiq-exec-overview`
- `grafana-provisioning/datasources/prometheus.yml`
  - Optional Grafana datasource provisioning file.
  - Creates datasource UID `prometheus`.
- `grafana-provisioning/dashboards/dashboards.yml`
  - Optional Grafana dashboard provider.
  - Expects dashboards mounted at `/var/lib/grafana/dashboards/lenseiq`.
- `prometheus/kafka-scrape-example.yml`
  - Example Prometheus scrape config for Kafka JMX exporter targets.
- `jmx-exporter/kafka-jmx-exporter.yml`
  - Kafka JMX exporter config that emits the required Kafka metrics.
- `reference/kafka-hot-topics-reference.html`
  - Static HTML reference of the current Hot Topics dashboard layout and tenant-prefix behavior.
- `reference/kafka-executive-overview-reference.html`
  - Static HTML reference of the current Executive Overview dashboard layout and cluster-health behavior.

## Option 1: Import In Grafana UI

1. Open Grafana.
2. Go to `Dashboards` -> `New` -> `Import`.
3. Upload `dashboards/kafka-hot-topics.json`.
4. Select your Prometheus datasource when prompted.
5. Repeat for `dashboards/kafka-executive-overview.json`.

This is the safest path if your Grafana datasource UID is not `prometheus`.

## HTML Reference

Open the files in `reference/` in a browser to view offline visual references of the dashboards. They are not wired to Prometheus; they are included to show the intended layouts, sample values, and dashboard semantics.

- `reference/kafka-hot-topics-reference.html`
- `reference/kafka-executive-overview-reference.html`

The Hot Topics reference includes the current tenant rule:

```text
tenant = topic prefix before the first dot or underscore
```

## Option 2: Provision Grafana From Files

Copy or mount the bundle files like this:

```text
/etc/grafana/provisioning/datasources/prometheus.yml
/etc/grafana/provisioning/dashboards/dashboards.yml
/var/lib/grafana/dashboards/lenseiq/kafka-hot-topics.json
/var/lib/grafana/dashboards/lenseiq/kafka-executive-overview.json
```

Then restart Grafana.

The included datasource provisioning file creates a Prometheus datasource with UID `prometheus`. If your existing Prometheus datasource has a different UID, either import through the UI or edit the dashboard datasource variable/default before provisioning.

## Prometheus Requirements

The dashboards expect these labels:

- `env`, for example `PROD`
- `job`, used as the cluster name
- `topic`, for per-topic metrics

Recommended topic naming shape:

```text
tenant.domain.event
```

Examples:

```text
channels.payment.events
csf.ledger.txn
express.shipments.events
```

The tenant dashboard variable derives tenant from the topic prefix before the first dot or underscore. The `job` label is still used as the cluster name.

## Required Metrics

The dashboards query these Prometheus metric names:

```text
kafka_server_brokertopicmetrics_bytesinpersec_oneminuterate
kafka_server_brokertopicmetrics_bytesoutpersec_oneminuterate
kafka_server_brokertopicmetrics_messagesinpersec_oneminuterate
kafka_server_brokertopicmetrics_totalproducerequestspersec_oneminuterate
kafka_server_brokertopicmetrics_totalfetchrequestspersec_oneminuterate
kafka_log_log_size
kafka_controller_kafkacontroller_activecontrollercount
kafka_controller_kafkacontroller_offlinepartitionscount
kafka_server_replicamanager_underreplicatedpartitions
```

If your JMX exporter emits camel/Pascal case metric names, use the `metric_relabel_configs` in `prometheus/kafka-scrape-example.yml` to normalize them for the dashboards.

## Kafka JMX Exporter

Use `jmx-exporter/kafka-jmx-exporter.yml` with the Prometheus JMX exporter Java agent on each broker, or adapt your existing JMX exporter rules so the metrics above exist with `topic` labels.

Example Kafka JVM option:

```bash
KAFKA_OPTS="-javaagent:/opt/jmx-exporter/jmx_prometheus_javaagent.jar=9404:/opt/jmx-exporter/kafka-jmx-exporter.yml"
```

Then configure Prometheus to scrape each broker's JMX exporter endpoint.

## Quick Validation

After Prometheus is scraping Kafka, test these queries in Prometheus:

```promql
count(count by (job) (kafka_controller_kafkacontroller_activecontrollercount{env="PROD"}))
topk(5, sum by (topic) ({__name__=~"kafka_server_brokertopicmetrics_bytes(in|out)persec_oneminuterate", env="PROD", topic!=""}))
```

If both queries return data, the dashboards should populate.
