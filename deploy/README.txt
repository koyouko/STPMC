============================================
 STP Kafka Mission Control - Deployment
============================================

OPTION A: Pre-built JAR (Java 17+ only)
----------------------------------------
1. Copy mission-control.jar + start.bat to a folder
2. Double-click start.bat
3. Opens http://localhost:8080

Prerequisites: Java 17+ only
  Install: winget install EclipseAdoptium.Temurin.17.JDK


OPTION B: Build from source
----------------------------
1. git clone https://github.com/koyouko/STPMC.git
2. cd STPMC && git checkout codex/initial-import
3. Double-click deploy\build-and-start.bat

Prerequisites: Java 17+ AND Node.js 20+
  Install: winget install EclipseAdoptium.Temurin.17.JDK
           winget install OpenJS.NodeJS.LTS

Note: Option B requires npm registry access. If your
corporate network blocks registry.npmjs.org, use Option A.


CONNECTING TO KAFKA CLUSTERS
-----------------------------
1. Open http://localhost:8080
2. Click "Onboard cluster"
3. Enter bootstrap server (e.g., kafka-prod:9092)
4. Select auth type (Plaintext / mTLS / Kerberos)
5. Click "Test Connection" then "Save"

DATABASE
---------
Default: H2 in-memory (resets on restart).
To persist data, switch to PostgreSQL:

Option 1: Docker Compose (local dev)
  docker compose up -d postgres
  SPRING_PROFILES_ACTIVE=postgres ./start.sh start

Option 2: External PostgreSQL (production / RHEL 8)
  DB_URL=jdbc:postgresql://your-host:5432/mission_control \
  DB_USERNAME=your_user \
  DB_PASSWORD=your_password \
  ./start.sh start
  (postgres profile is auto-activated when DB_URL contains "postgresql")

First run: Tables are created automatically (ddl-auto=update).
Production: After initial setup, set HIBERNATE_DDL_AUTO=validate
            to prevent schema changes on startup.

Migration note: If upgrading from a previous version, run:
  ALTER TABLE clusters ADD COLUMN jmx_cluster_id VARCHAR(255);
