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

### Frontend

```bash
cd /Users/rajeev/Documents/Project/LenseIQ/frontend
npm install
npm run dev
```

The Vite dev server runs on `http://localhost:5173` and proxies `/api` to the backend.

## External API example

Create a service account and token in the UI, then call:

```bash
curl -H "Authorization: Bearer stpmc_<token>" \
  http://localhost:8080/api/external/v1/clusters/health
```

## Current implementation notes

- The backend is production-oriented in domain shape, but still a v1 foundation. SAML/ADFS integration is represented in the architecture and security mode switch, while development mode is the default runnable profile in this repo.
- Payload decoding with Confluent Schema Registry SerDes is not wired yet.
- Topic list, ACL list, consumer describe, and topic dump task execution are planned but not yet implemented in this baseline.
- Mounted secret files are modeled and consumed for Kafka health checks; secret rotation/orchestration remains an infrastructure concern outside the app.

## Verification

```bash
cd /Users/rajeev/Documents/Project/LenseIQ/backend && ./mvnw test
cd /Users/rajeev/Documents/Project/LenseIQ/frontend && npm run build
```
