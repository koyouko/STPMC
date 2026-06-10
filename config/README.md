# config/

Runtime configuration for the local Kafka JMX exporter integration.

## kafka_broker.yml

Prometheus JMX Exporter config mirroring the Confluent-style
`kafka_broker.yml` used in production. Intentionally sets
`lowercaseOutputName: true` and `lowercaseOutputLabelNames: true`
so the scraper's case-insensitive metric lookup path is exercised.
Mounted by `docker-compose.yml` into all three `kafka1/2/3` brokers.

## JMX Prometheus Java Agent JAR (not checked in)

`jmx_prometheus_javaagent-*.jar` is listed in `.gitignore` (licensing +
binary hygiene). Before starting the local Kafka brokers, fetch the
pinned version once:

```bash
# From the repo root
curl -fSL \
  -o config/jmx_prometheus_javaagent-0.15.0.jar \
  https://repo1.maven.org/maven2/io/prometheus/jmx/jmx_prometheus_javaagent/0.15.0/jmx_prometheus_javaagent-0.15.0.jar
```

0.15.0 is intentional — it matches the production-deployed version and
faithfully reproduces its quirks (e.g. `_objectname` fallback labels on
String JMX attributes, `jvm_memory_bytes_used` naming). Newer releases
(1.x) change both.

After the JAR is in place:

```bash
docker compose up -d kafka1 kafka2 kafka3
```

## jmx-exporter.yml (legacy)

The older PascalCase variant used before the scraper was hardened for
lowercase-output configs. Kept as documentation of the alternate code
path; not mounted by docker-compose any more.
