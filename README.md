# STP Kafka Mission Control

STP Kafka Mission Control is a greenfield control-plane baseline for existing Confluent Kafka estates. This repo includes:

- A Spring Boot backend with cluster onboarding models, cached cluster/component health snapshots, external health APIs, service-account token auth, audit events, and native Kafka client integration scaffolding.
- A React frontend with a Lenses-inspired operational shell for fleet rollups, cluster health detail, and service-account token generation.
- Local development defaults for H2 plus a `docker-compose.yml` Postgres option for persistent runtime testing.

## What is implemented

- `GET /api/external/v1/clusters/health`
- `GET /api/external/v1/clusters/{id}/health`
- `POST /api/external/v1/clusters/{id}/health/refresh`
- `POST /api/admin/service-accounts`
- `POST /api/admin/service-accounts/{id}/tokens`
- `DELETE /api/admin/service-accounts/{id}/tokens/{tokenId}`
- `GET /api/admin/service-accounts`
- `GET /api/platform/clusters`
- `GET /api/platform/clusters/{id}/health`
- `POST /api/platform/clusters`

The health engine supports:

- Kafka health via `org.apache.kafka:kafka-clients` `AdminClient`
- mTLS and Kerberos auth profile modeling
- HTTP and TCP checks for ZooKeeper, Schema Registry, Control Center, and Prometheus
- Cached snapshots plus async force-refresh operations
- Machine-to-machine bearer tokens with hashed persistence and scope checks

## Local run

## Windows prerequisites

For a Windows workstation, install these first:

- Java JDK 17
- Node.js 22 LTS
- Git
- Maven 3.9.12 recommended if you want to run Maven manually

Important Maven note:

- Spring Boot `3.3.5` supports Maven `3.6.3+`
- This repo's Maven wrapper is pinned to Apache Maven `3.9.12`
- If you install Maven manually on Windows, use `3.9.12` so it matches the wrapper behavior exactly

Quick version checks in PowerShell:

```powershell
java -version
node -v
npm -v
mvn -v
```

### Backend

```bash
cd /Users/rajeev/Documents/Project/LenseIQ/backend
./mvnw spring-boot:run
```

Default backend behavior:

- Runs on `http://localhost:8080`
- Uses in-memory H2 by default
- Seeds two demo clusters and one demo service account record
- Uses development auth for platform/admin APIs via request headers or the built-in local admin fallback
- If Kafka is available on `localhost:9092` and Schema Registry is available on `http://localhost:8081`, the app auto-seeds a `Local Kafka Dev` cluster and checks real health instead of demo-only placeholder health

Optional Postgres:

```bash
cd /Users/rajeev/Documents/Project/LenseIQ
docker compose up -d
cd /Users/rajeev/Documents/Project/LenseIQ/backend
DB_URL=jdbc:postgresql://localhost:5432/mission_control \
DB_USERNAME=mission_control \
DB_PASSWORD=mission_control \
./mvnw spring-boot:run
```

### Windows backend commands

Use the Maven wrapper on Windows like this:

```powershell
cd C:\path\to\STPMC\backend
.\mvnw.cmd spring-boot:run
```

If you installed Maven manually instead of using the wrapper:

```powershell
cd C:\path\to\STPMC\backend
mvn spring-boot:run
```

Or from the repo root:

```powershell
.\run-backend.bat
```

### Frontend

```bash
cd /Users/rajeev/Documents/Project/LenseIQ/frontend
npm install
npm run dev
```

The Vite dev server runs on `http://localhost:5173` and proxies `/api` to the backend.

### Windows frontend commands

```powershell
cd C:\path\to\STPMC\frontend
npm install
npm run dev
```

Or from the repo root:

```powershell
.\run-frontend.bat
```

## External API example

Create a service account and token in the UI, then call:

```bash
curl -H "Authorization: Bearer stpmc_<token>" \
  http://localhost:8080/api/external/v1/clusters/health
```

## Metrics Scraping

Upload a CSV inventory of broker JMX exporter endpoints, then trigger a scrape. The scraper:

1. Fetches `/metrics` from each target in parallel (bounded pool of 10, 120s overall timeout)
2. Extracts `kafka_server_KafkaServer_ClusterId` to auto-discover cluster membership
3. Writes back discovered cluster IDs to each target
4. Auto-onboards new clusters (creates Cluster entity with JMX cluster ID for dedup)

**CSV format:** `clusterName, host, port (default 9404), role (default BROKER), environment (default NON_PROD)`

## Health Refresh Cycle

| Setting | Env Var | Default |
|---------|---------|---------|
| Poll interval | `APP_HEALTH_POLL_INTERVAL_MS` | 60,000 ms (1 min) |
| Stale threshold | `APP_HEALTH_STALE_AFTER_MS` | 180,000 ms (3 min) |
| Refresh cooldown | `APP_HEALTH_REFRESH_COOLDOWN_MS` | 60,000 ms (1 min) |
| Kafka timeout | `APP_KAFKA_TIMEOUT_MS` | 4,000 ms |
| Probe timeout | `APP_PROBE_TIMEOUT_MS` | 3,000 ms |

Every poll interval, the scheduler refreshes all active clusters: probes Kafka via `AdminClient`, then probes each configured service endpoint (HTTP or TCP). Components with no endpoint are marked `NOT_APPLICABLE` and hidden in the UI.

## Security

| Mode | Env Var | Behavior |
|------|---------|----------|
| `saml` (default) | `APP_SECURITY_MODE=saml` | SAML SSO, CSRF enabled, loopback blocked |
| `development` | `APP_SECURITY_MODE=development` | Auto-auth with full roles, CSRF disabled |

### SSRF Protection

- Host validation at CSV upload **and** re-validation at scrape time (DNS rebinding defense)
- Cloud metadata IPs blocked (`169.254.169.254`, `fd00:ec2::254`)
- Loopback blocked in production (SAML mode)
- RFC 1918 private IPs allowed by design (scraping internal Kafka brokers)
- H2 console explicitly denied regardless of configuration

### Secrets

Secret files (truststore/keystore passwords) must reside under the configured base directory:

```
APP_SECRETS_BASE_DIR=/etc/secrets  # default
```

Path traversal outside this directory is rejected.

### JAAS Configuration

Kerberos keytab paths and principals are validated against an allowlist pattern (`a-z A-Z 0-9 _ . / @ : -`). Characters like `"`, `;`, and `\` are rejected to prevent JAAS config injection.

## Production Deployment

Set `HIBERNATE_DDL_AUTO=validate` (default). Run this migration for JMX auto-onboard dedup:

```sql
ALTER TABLE clusters ADD COLUMN jmx_cluster_id VARCHAR(255);
```

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_URL` | H2 in-memory | JDBC URL (PostgreSQL recommended for prod) |
| `DB_USERNAME` / `DB_PASSWORD` | sa / (empty) | Database credentials |
| `APP_SECURITY_MODE` | saml | `saml` or `development` |
| `APP_ALLOWED_ORIGIN` | http://localhost:5173 | CORS allowed origin |
| `APP_SECRETS_BASE_DIR` | /etc/secrets | Base directory for secret files |
| `H2_CONSOLE_ENABLED` | false | H2 web console (denied in security config regardless) |

## Current implementation notes

- The backend is production-oriented in domain shape, but still a v1 foundation. SAML/ADFS integration is represented in the architecture and security mode switch, while development mode is the default runnable profile in this repo.
- Payload decoding with Confluent Schema Registry SerDes is not wired yet.
- Topic list, ACL list, consumer describe, and topic dump task execution are planned but not yet implemented in this baseline.
- Mounted secret files are modeled and consumed for Kafka health checks; secret rotation/orchestration remains an infrastructure concern outside the app.

## Verification

```bash
cd backend && ./mvnw test        # 20 tests: security, API, service layer
cd frontend && npm run build     # TypeScript compile + Vite production build
```
