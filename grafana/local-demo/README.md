# LenseIQ Hot Topics — local demo stack

A fully self-contained, **synthetic** version of the LenseIQ Hot Topics + Executive
dashboards. One command brings up Prometheus + Grafana with **30 days of
backfilled history** and a live exporter, so every panel — including the
30-day trend and the "trending up" table — is populated the moment it boots.

No real Kafka cluster required. The data is generated, but it flows through the
exact same path your production setup uses: Prometheus scraping Confluent
JMX-style metrics, the LenseIQ dashboards querying them.

## Quick start

```bash
cd grafana/local-demo
docker compose up -d --build
```

First boot takes ~2–4 minutes (it builds the exporter image, generates 30 days
of history, and backfills it into Prometheus). Then open:

| URL | What |
|---|---|
| http://localhost:3000 | Grafana (anonymous Admin — no login needed; or `admin`/`admin`) |
| http://localhost:9090 | Prometheus (to poke at raw queries) |

The two dashboards are auto-provisioned under the **LenseIQ** folder. Grafana
opens on **LenseIQ — Executive Overview** by default.

When you're done:

```bash
docker compose down -v        # -v also wipes the backfilled TSDB + Grafana state
```

## What you'll see

12 synthetic clusters across 4 tenants (CHANNELS, CSF, DDA, EXPRESS), ~90
topics, with realistic shape baked in:

- **Diurnal + weekly traffic** — mid-day peaks, quieter weekends.
- **A trending-up topic** — `express.shipments.events` ramps over the last ~2 days, so it tops the "trending up" table (24h vs 7d).
- **A hot topic** — `channels.payment.events` is the throughput leader (~142 MB/s).
- **Health anomalies for the exec view** — `EXPRESS_EMEA_PROD_COB_PHY` is **down** (offline partitions, started 2 days ago), `CHANNELS_NAM_PROD_COB_Prober` and `CSF_NAM_PROD_COB` are **degraded** (under-replicated). Current rollup: 12 total / 9 healthy / 2 degraded / 1 down.

Set the Grafana time range to **Last 30 days** to see the full trend history.

## Demo flow

1. Land on **Executive Overview** → stat row reads 12 / 9 / 2 / 1. The estate health history shows the green band narrowing ~2 days ago when EMEA went down.
2. In the cluster table, sort by **Offline** desc → `EXPRESS_EMEA_PROD_COB_PHY` on top. Click its name → jumps to **Hot Topics** scoped to that cluster.
3. On **Hot Topics**, pick `CHANNELS_NAM_PROD_COB_PHY` in the `cluster` variable, range 30d → top-5 bar gauges, the multi-line trend, and the donut all fill in.
4. Look at **Trending up** → `express.shipments.events` leads at ~+32%.
5. Set the `topic` variable to `express.shipments.events` → the bottom drill-down panel renders its 30-day bytes-in/out/messages trend.

## How it works

```
gen-history  ─ generates 30d OpenMetrics file ─▶  (history volume)
                                                       │
backfill     ─ promtool tsdb create-blocks-from ─▶  (prom-data volume)
                                                       │
exporter     ─ live /metrics for "now" ───────┐       │
                                              ▼       ▼
prometheus   ─ scrapes exporter (honor_labels) + loads backfilled blocks
                                              │
grafana      ─ provisioned datasource + both dashboards
```

`exporter/topology.py` is the single source of truth: every series is a pure
function of time, so the backfilled history and the live scrape join into one
continuous line with no seam.

- `exporter/topology.py` — clusters, tenants, topics, and the time→value model
- `exporter/exporter.py` — live Prometheus exposition endpoint (:9404)
- `exporter/generate_history.py` — writes the 30-day OpenMetrics backfill file
- `prometheus/prometheus.yml` — scrape config (`honor_labels: true` keeps the exporter's `env`/`job` labels)
- `grafana/provisioning/...` — datasource (uid `PROMETHEUS_LOCAL`) + dashboard providers
- `docker-compose.yml` — orchestrates the five services with ordered startup

Prometheus runs with `--storage.tsdb.retention.time=60d` so the 30-day backfill
isn't pruned (the default retention is only 15d).

## Tweaking the demo

All knobs live in `exporter/topology.py`:

- **Add/remove clusters** → edit `CLUSTERS` (job, tenant, scale, env, health).
- **Change topics or rates** → edit `TENANT_TOPICS` (base MB/s, out ratio, msg size, log GB, trend).
- **Make a topic trend harder** → raise its `trend` value (0 = flat, 1.6 = the EXPRESS star).
- **Change health states** → edit `_offline`, `_urp`, `_controllers`.
- **History length / resolution** → `HISTORY_DAYS` (default 30) and `HISTORY_STEP_SECONDS` (default 3600).

After editing, rebuild and re-backfill:

```bash
docker compose down -v
docker compose up -d --build
```

## Validation already done

This stack was tested before delivery (Prometheus v2.54.1 / promtool):

- Exporter exposition format passes `promtool check metrics` (756 series/scrape).
- 30-day history (544k samples) backfills via `promtool tsdb create-blocks-from openmetrics` into valid TSDB blocks.
- The actual dashboard PromQL — top-N, trending-up (24h/7d), tenant `label_replace`, and the executive health classification — all return correct results against the backfilled data.

## Note vs. a real local broker

This is the **synthetic** stack (multi-cluster, instant 30-day history) — the
right tool for demoing hot topics and trends. It does **not** stand up a real
Kafka broker. If you also want a single real KRaft broker labelled
`job="LOCAL_lenseiq-kafka"` (per the note in `../README.md`), that's a separate
add-on — say the word and I'll wire a `kafka` + JMX-exporter service alongside
this one.
