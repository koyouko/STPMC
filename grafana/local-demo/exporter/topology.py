"""
LenseIQ synthetic Kafka topology + metric value model.

Single source of truth shared by:
  - exporter.py            (live /metrics endpoint, value at "now")
  - generate_history.py    (30-day OpenMetrics backfill)

The value of every series is a *pure function of time*, so the backfilled
history and the live scrape join into one continuous line with no seam.

Metric names match the LenseIQ Grafana dashboards (Confluent JMX exporter,
lowercased). Every series carries `env` and `job` labels; topic-level series
add `topic`; log size adds `partition`.
"""

import hashlib
import math
import time

# ── Tunables ────────────────────────────────────────────────────────────
HISTORY_DAYS = 30          # how far back the backfill goes
HISTORY_STEP_SECONDS = 3600  # 1 sample/hour in history (live fills recent detail)
PARTITIONS_PER_TOPIC = 3   # for kafka_log_log_size skew

# ── Topic catalogs per tenant ───────────────────────────────────────────
# (name, base_in_MBps, out_ratio, avg_msg_bytes, log_gb, trend)
#   trend > 0 makes the topic ramp over the last ~2 days -> shows as "trending up"
TENANT_TOPICS = {
    "CHANNELS": [
        ("channels.payment.events",   22.0, 1.20,  850, 600, 0.20),
        ("channels.notify.outbound",   9.0, 1.10, 1200, 180, 0.00),
        ("channels.session.audit",     5.0, 0.80,  400, 240, 0.00),
        ("channels.fx.quotes",         7.0, 1.40,  300,  90, 0.00),
        ("channels.statement.render",  3.0, 1.00, 4096, 120, 0.00),
        ("channels.login.events",      2.5, 0.70,  350,  60, 0.00),
        ("channels.fraud.signals",     4.0, 1.10,  700,  80, 0.35),
        ("channels.cards.auth",        6.0, 1.20,  600, 110, 0.00),
        ("channels.dispute.workflow",  1.5, 0.90, 1500,  40, 0.00),
    ],
    "CSF": [
        ("csf.ledger.txn",            16.0, 1.15,  700, 520, 0.00),
        ("csf.posting.events",         8.0, 1.00,  900, 200, 0.00),
        ("csf.reconciliation",         4.0, 0.90, 2048, 160, 0.00),
        ("csf.balance.snapshot",       3.0, 1.20, 1024, 300, 0.00),
        ("csf.gl.feed",                5.0, 1.10,  800, 140, 0.15),
        ("csf.audit.trail",            2.0, 0.60,  500,  90, 0.00),
        ("csf.fee.calc",               1.8, 1.00,  650,  40, 0.00),
    ],
    "DDA": [
        ("dda.account.updates",        6.0, 1.10,  600, 130, 0.00),
        ("dda.txn.stream",             9.0, 1.20,  500, 210, 0.25),
        ("dda.overdraft.events",       1.2, 0.90,  700,  30, 0.00),
        ("dda.statement.cycle",        2.0, 1.00, 4096,  70, 0.00),
        ("dda.kyc.refresh",            1.5, 0.80, 1500,  50, 0.00),
        ("dda.alerts.outbound",        2.5, 1.00,  400,  45, 0.00),
    ],
    "EXPRESS": [
        ("express.shipments.events",   8.0, 1.25,  550, 190, 1.60),  # trending star
        ("express.tracking.updates",  11.0, 1.30,  350, 160, 0.40),
        ("express.label.print",        3.0, 1.00, 2048,  80, 0.00),
        ("express.customs.clear",      2.0, 0.90, 1200,  60, 0.00),
        ("express.delivery.proof",     4.0, 1.10,  900, 100, 0.00),
        ("express.route.optimize",     1.5, 0.80, 1500,  40, 0.00),
        ("express.exception.queue",    1.0, 0.70,  800,  25, 0.30),
    ],
}

# ── Clusters ────────────────────────────────────────────────────────────
# (job, tenant, scale, env, health)
#   health: "healthy" | "degraded" | "down"
CLUSTERS = [
    ("CHANNELS_NAM_PROD_COB_PHY",     "CHANNELS", 1.80, "PROD", "healthy"),
    ("CHANNELS_IDL_PROD_COB_PHY",     "CHANNELS", 1.00, "PROD", "healthy"),
    ("CHANNELS_EMEA_PROD_COB_PHY",    "CHANNELS", 0.90, "PROD", "healthy"),
    ("CHANNELS_NAM_PROD_COB_Prober",  "CHANNELS", 0.25, "PROD", "degraded"),
    ("CSF_NAM_PROD_COB",              "CSF",      1.30, "PROD", "degraded"),
    ("CSF_APAC_PROD_COB",             "CSF",      0.60, "PROD", "healthy"),
    ("DDA_NAM_PROD_VM",               "DDA",      0.70, "PROD", "healthy"),
    ("DDA_EMEA_PROD_VM",              "DDA",      0.50, "PROD", "healthy"),
    ("EXPRESS_APAC_PROD_COB_PHY",     "EXPRESS",  1.10, "PROD", "healthy"),
    ("EXPRESS_APAC_PROD_COB_VM",      "EXPRESS",  0.80, "PROD", "healthy"),
    ("EXPRESS_NAM_PROD_COB_PHY",      "EXPRESS",  1.00, "PROD", "healthy"),
    ("EXPRESS_EMEA_PROD_COB_PHY",     "EXPRESS",  0.90, "PROD", "down"),
]

PARTITION_SKEW = [0.50, 0.30, 0.20]  # how log size splits across 3 partitions

# Metric name constants (match the dashboards exactly)
M_BYTES_IN  = "kafka_server_brokertopicmetrics_bytesinpersec_oneminuterate"
M_BYTES_OUT = "kafka_server_brokertopicmetrics_bytesoutpersec_oneminuterate"
M_MSGS_IN   = "kafka_server_brokertopicmetrics_messagesinpersec_oneminuterate"
M_PROD_REQ  = "kafka_server_brokertopicmetrics_totalproducerequestspersec_oneminuterate"
M_FETCH_REQ = "kafka_server_brokertopicmetrics_totalfetchrequestspersec_oneminuterate"
M_LOG_SIZE  = "kafka_log_log_size"
M_CTRL      = "kafka_controller_kafkacontroller_activecontrollercount"
M_OFFLINE   = "kafka_controller_kafkacontroller_offlinepartitionscount"
M_URP       = "kafka_server_replicamanager_underreplicatedpartitions"

GAUGE_METRICS = [
    M_BYTES_IN, M_BYTES_OUT, M_MSGS_IN, M_PROD_REQ, M_FETCH_REQ,
    M_LOG_SIZE, M_CTRL, M_OFFLINE, M_URP,
]

DAY = 86400.0


# ── Shape functions ─────────────────────────────────────────────────────
def _hash01(s: str) -> float:
    d = hashlib.md5(s.encode()).digest()
    return int.from_bytes(d[:4], "big") / 2 ** 32


def _diurnal(t: float) -> float:
    hour = (t / 3600.0) % 24.0
    return 1.0 + 0.45 * math.sin(2 * math.pi * (hour - 8.0) / 24.0)


def _weekly(t: float) -> float:
    wday = time.gmtime(t).tm_wday  # Mon=0 .. Sun=6
    return 0.65 if wday in (5, 6) else 1.0


def _noise(key: str, t: float, bucket_s: int = 300) -> float:
    bucket = int(t // bucket_s)
    return 1.0 + 0.12 * (_hash01(f"{key}|{bucket}") - 0.5) * 2.0


def _trend(t: float, now: float, trend: float) -> float:
    if trend <= 0:
        return 1.0
    age_h = max(0.0, (now - t) / 3600.0)
    return 1.0 + trend * math.exp(-age_h / 36.0)


def _throughput(job, topic, base_in_mbps, out_ratio, msg_bytes, scale, trend,
                metric, t, now):
    key = f"{job}|{topic}|{metric}"
    common = _diurnal(t) * _weekly(t) * _noise(key, t) * _trend(t, now, trend) * scale
    bytes_in = base_in_mbps * 1e6 * common
    if metric == M_BYTES_IN:
        return bytes_in
    if metric == M_BYTES_OUT:
        return bytes_in * out_ratio
    if metric == M_MSGS_IN:
        return bytes_in / msg_bytes
    if metric == M_PROD_REQ:
        return bytes_in / (msg_bytes * 100.0)            # ~100 msgs/batch
    if metric == M_FETCH_REQ:
        return (bytes_in * out_ratio) / (msg_bytes * 100.0) * 2.5  # consumer fanout
    return 0.0


def _log_size_total(base_log_gb, scale, t, now):
    # Slow monotonic growth over the retention window + gentle noise; no diurnal.
    days_in = max(0.0, (t - (now - HISTORY_DAYS * DAY)) / DAY)
    growth = 1.0 + 0.012 * days_in
    return base_log_gb * 1e9 * scale * growth


# ── Health timelines (control-plane metrics) ────────────────────────────
def _offline(job, health, t, now):
    if health == "down" and t > now - 2 * DAY:
        return 12.0
    return 0.0


def _urp(job, health, t, now):
    base = 0.0
    if job == "CHANNELS_NAM_PROD_COB_Prober" and t > now - 3 * DAY:
        base = 24.0
    elif job == "CSF_NAM_PROD_COB":
        base = 6.0 if math.sin(t / 5400.0) > 0.2 else 0.0   # flapping URP
    # transient spike on an otherwise-healthy cluster ~6 days ago (history interest)
    if job == "CHANNELS_NAM_PROD_COB_PHY" and now - 6.2 * DAY < t < now - 5.9 * DAY:
        base = 15.0
    return base


def _controllers(job, health, t, now):
    # brief controller election gap on the down cluster at outage onset
    if health == "down" and now - 2 * DAY < t < now - 2 * DAY + 1800:
        return 0.0
    return 1.0


# ── Public iterators ────────────────────────────────────────────────────
def iter_samples(t: float, now: float):
    """Yield (metric_name, labels_dict, value) for every series at time t."""
    for job, tenant, scale, env, health in CLUSTERS:
        base_lbl = {"env": env, "job": job}

        # control-plane (per cluster)
        yield M_CTRL,    dict(base_lbl), _controllers(job, health, t, now)
        yield M_OFFLINE, dict(base_lbl), _offline(job, health, t, now)
        yield M_URP,     dict(base_lbl), _urp(job, health, t, now)

        # per-topic
        for (name, base_in, out_ratio, msg_bytes, log_gb, trend) in TENANT_TOPICS[tenant]:
            tlbl = {"env": env, "job": job, "topic": name}
            for metric in (M_BYTES_IN, M_BYTES_OUT, M_MSGS_IN, M_PROD_REQ, M_FETCH_REQ):
                val = _throughput(job, name, base_in, out_ratio, msg_bytes,
                                  scale, trend, metric, t, now)
                yield metric, dict(tlbl), val

            total_log = _log_size_total(log_gb, scale, t, now)
            for p in range(PARTITIONS_PER_TOPIC):
                plbl = {"env": env, "job": job, "topic": name, "partition": str(p)}
                pnoise = _noise(f"{job}|{name}|log|{p}", t, 3600)
                yield M_LOG_SIZE, plbl, total_log * PARTITION_SKEW[p] * pnoise


def series_count() -> int:
    return sum(1 for _ in iter_samples(now=time.time(), t=time.time()))


if __name__ == "__main__":
    n = series_count()
    print(f"clusters={len(CLUSTERS)} series_per_scrape={n}")
    pts = int(HISTORY_DAYS * DAY / HISTORY_STEP_SECONDS)
    print(f"history points/series={pts} approx_total_samples={n * pts:,}")
