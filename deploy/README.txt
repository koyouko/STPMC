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

Option 3: External Oracle (production / RHEL 8)
  See oracle-database-team-request.md for the DB team setup request
  and email template.
  sqlplus STP_KAFKA_HC_MISSION_CONTROL/your_password@//your-host:1521/YOUR_SERVICE @oracle-schema.sql
  DB_URL=jdbc:oracle:thin:@//your-host:1521/YOUR_SERVICE \
  DB_USERNAME=STP_KAFKA_HC_MISSION_CONTROL \
  DB_PASSWORD=your_password \
  DB_SCHEMA=STP_KAFKA_HC_MISSION_CONTROL \
  SPRING_PROFILES_ACTIVE=oracle \
  HIBERNATE_DDL_AUTO=validate \
  ./start.sh start
  (oracle profile is auto-activated when DB_URL starts with "jdbc:oracle")
  Update DB_URL, DB_USERNAME, DB_PASSWORD, and DB_SCHEMA in the runtime environment.
  DB_USERNAME is the login account; DB_SCHEMA owns the tables.
  For AD login, use DB_USERNAME=stcmc and DB_SCHEMA=STP_KAFKA_HC_MISSION_CONTROL.
  Do not hardcode real passwords in application-oracle.yml or commit them.

PostgreSQL first run: Tables are created automatically when ddl-auto=update.
Oracle production: Create or migrate the schema separately, then keep
                   HIBERNATE_DDL_AUTO=validate.

Oracle troubleshooting: If startup fails with
  Schema-validation: missing table [audit_events]
the app connected to Oracle, but DB_SCHEMA does not contain the tables or the
DDL has not been applied. Check:
  SELECT owner, table_name FROM all_tables WHERE table_name = 'AUDIT_EVENTS';

Oracle troubleshooting: If startup fails with
  Failed to load driver class oracle.jdbc.OracleDriver
delete the old deploy/mission-control.jar and run start.sh/start.bat again,
or rebuild from source with:
  bash deploy/build-bundle.sh

Migration note: If upgrading from a previous version, run:
  ALTER TABLE clusters ADD COLUMN jmx_cluster_id VARCHAR(255);
