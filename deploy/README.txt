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

Database: H2 in-memory (resets on restart)
To persist data, set environment variables for PostgreSQL:
  set SPRING_DATASOURCE_URL=jdbc:postgresql://host:5432/missioncontrol
  set SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.postgresql.Driver
  set SPRING_DATASOURCE_USERNAME=postgres
  set SPRING_DATASOURCE_PASSWORD=yourpassword
