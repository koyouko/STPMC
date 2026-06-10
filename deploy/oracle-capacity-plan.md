# Oracle Capacity Forecast

Prepared for the Oracle database team for the LenseIQ / Kafka Mission Control
backend.

## Executive Summary

The application stores Kafka control-plane configuration, service-account token
metadata, current health snapshots, audit events, refresh operation history, and
JMX metrics target inventory. It does not store Kafka messages, Kafka topic
payloads, Prometheus time-series samples, or scraped broker metric history.

Most tables are small inventory/configuration tables. The sustained growth is
from:

- `STP_Kafka_HC_health_refresh_operations`
- `STP_Kafka_HC_audit_events`

At the default health poll interval of 60 seconds, each active cluster creates
one health refresh operation row and one audit event row per minute.

## Runtime Assumptions

| Item | Default / planning value |
| --- | --- |
| Application profile | `oracle` |
| DDL mode | `HIBERNATE_DDL_AUTO=validate`; application does not create or alter objects |
| Table prefix | `STP_KAFKA_HC_` |
| Health polling interval | 60 seconds by default via `APP_HEALTH_POLL_INTERVAL_MS=60000` |
| JDBC pool | 10 max connections by default via `DB_POOL_SIZE=10` |
| Background health executor | 4 core threads, 12 max threads, 100 queue capacity |
| Metrics scrape targets | Application limit is 500 configured targets |
| Metric sample persistence | Not persisted; samples are returned to API callers and discarded |

## Data Growth Formula

For active cluster count `C` and health poll interval `P` seconds:

```text
refreshes_per_day = C * 86400 / P
health_refresh_operation_rows_per_day = refreshes_per_day
audit_event_rows_per_day ~= refreshes_per_day + operator/admin actions
```

For planning, use 4.5 KB per scheduled refresh cycle per cluster. This includes
one `health_refresh_operations` row, one `audit_events` row, row overhead, and
supporting index growth. Actual segment growth should be measured after the
first 30 days and adjusted using Oracle segment statistics.

## Forecast Scenarios

These estimates assume the default 60-second health poll interval and no purge
of history rows.

| Scenario | Active clusters | Refreshes/day | Growing rows/day | Estimated growth/day | 90-day growth | 1-year growth |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Pilot | 25 | 36,000 | 72,000 | 158 MB | 14 GB | 56 GB |
| Expected production | 100 | 144,000 | 288,000 | 633 MB | 56 GB | 226 GB |
| High production | 250 | 360,000 | 720,000 | 1.55 GB | 139 GB | 564 GB |

The estimates above are intentionally conservative. The application writes
short status/audit messages most of the time, but `audit_events.details` allows
up to 2,000 characters.

## Stable Tables

The following tables grow mostly with operator configuration activity and are
expected to stay small:

| Table group | Expected size driver |
| --- | --- |
| `STP_Kafka_HC_clusters` | One row per onboarded Kafka cluster |
| `STP_Kafka_HC_cluster_auth_profiles` | Usually one to three rows per cluster |
| `STP_Kafka_HC_cluster_listeners` | Usually one to three rows per cluster |
| `STP_Kafka_HC_service_endpoints` | Usually zero to eight rows per cluster |
| `STP_Kafka_HC_cluster_health_snapshots` | One current snapshot row per cluster |
| `STP_Kafka_HC_component_health_snapshots` | Current component rows only, typically seven to twelve rows per cluster |
| `STP_Kafka_HC_metrics_targets` | One row per broker/JMX target, capped by the app at 500 |
| `STP_Kafka_HC_service_accounts` and child tables | Usually tens to hundreds of rows |
| `STP_Kafka_HC_service_account_tokens` | Usually tens to hundreds of rows |

Health component snapshots are replaced during refresh; they are not a
time-series table.

## Workload And Sessions

The application uses a Hikari JDBC pool with `DB_POOL_SIZE=10` by default.
Please plan for:

- 10 database sessions per application instance.
- 15 to 20 sessions per environment if allowing DBA/admin headroom for one app
  instance.
- Multiply by the number of application instances if the service is horizontally
  deployed.

The default scheduler queues one refresh per active cluster every minute. For
100 clusters, this is roughly 144,000 refresh operations per day, or about 100
scheduled refreshes per minute. The executor limits concurrent refresh work to a
maximum of 12 threads, so database write pressure is bounded by the application
thread pool and JDBC pool.

At expected production scale, database write load is modest:

- Around 200 sustained insert rows per minute for scheduler history/audit at
  100 active clusters.
- Additional updates to current health snapshot rows.
- Occasional operator-driven configuration writes.
- Read workload is mostly UI/API reads by primary key, active cluster list, and
  health detail queries.

## Retention Recommendation

Without retention, history tables grow indefinitely. Recommended retention:

| Table | Suggested retention | Reason |
| --- | --- | --- |
| `STP_Kafka_HC_health_refresh_operations` | 90 days | Operational troubleshooting history |
| `STP_Kafka_HC_audit_events` | 365 days, or enterprise audit policy | Security and operator audit trail |

Example DBA-managed purge statements, subject to enterprise retention policy:

```sql
DELETE FROM STP_Kafka_HC_health_refresh_operations
WHERE requested_at < SYSTIMESTAMP - INTERVAL '90' DAY;

DELETE FROM STP_Kafka_HC_audit_events
WHERE created_at < SYSTIMESTAMP - INTERVAL '365' DAY;
```

If audit policy requires longer retention, size the schema using the 1-year
growth column above or extend it linearly.

## Tablespace Recommendation

For production planning, use the active cluster count and retention target:

| Deployment | Suggested initial allocation |
| --- | --- |
| Pilot or UAT, up to 25 clusters | 25 GB with autoextend |
| Expected production, around 100 clusters, 90-day operational retention | 100 GB with autoextend |
| Expected production, around 100 clusters, no purge for 1 year | 300 GB with autoextend |
| High production, around 250 clusters, no purge for 1 year | 700 GB with autoextend |

These values include headroom above the table forecast for indexes, segment
overhead, future columns, and normal variance. The DB team may split audit and
operation-history objects into separate tablespaces if local standards require
separate retention or compression policies.

## Monitoring Queries

After deployment, these queries can be used to validate growth:

```sql
SELECT segment_name,
       ROUND(bytes / 1024 / 1024, 2) AS mb
FROM user_segments
WHERE segment_name LIKE 'STP_KAFKA_HC_%'
ORDER BY bytes DESC;

SELECT COUNT(*) AS health_refresh_operations
FROM STP_Kafka_HC_health_refresh_operations;

SELECT COUNT(*) AS audit_events
FROM STP_Kafka_HC_audit_events;

SELECT TRUNC(requested_at) AS day,
       COUNT(*) AS refresh_operations
FROM STP_Kafka_HC_health_refresh_operations
GROUP BY TRUNC(requested_at)
ORDER BY day DESC;

SELECT TRUNC(created_at) AS day,
       COUNT(*) AS audit_events
FROM STP_Kafka_HC_audit_events
GROUP BY TRUNC(created_at)
ORDER BY day DESC;
```

Revisit capacity after 30 days of production traffic and update the forecast
using actual segment growth and actual active cluster count.
