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
