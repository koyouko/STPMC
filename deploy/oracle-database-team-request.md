# Oracle Database Setup Request

This document is for the Oracle database team that will provision the database
used by LenseIQ / Kafka Mission Control.

## Summary

LenseIQ needs an Oracle schema for the backend service. The application stores
cluster configuration, service accounts, audit events, health snapshots, and JMX
metrics target inventory. It does not store Kafka message payloads.

The backend runs on Java 17 with Spring Boot and Hibernate. The Oracle runtime
uses the `oracle` Spring profile, the Oracle JDBC `ojdbc11` driver, and
`org.hibernate.dialect.OracleDialect`.

## Requested Database

| Item | Requested value |
| --- | --- |
| Environment | `<DEV / UAT / PROD>` |
| Oracle version | 19c or newer preferred |
| Character set | AL32UTF8 preferred |
| Service name | To be provided by DB team |
| Listener protocol | `TCP` or `TCPS`, per enterprise standard |
| Listener port | Usually `1521` for TCP or enterprise TCPS port |
| Suggested schema/user | `MISSION_CONTROL` or DB-team approved equivalent |
| Runtime DDL mode | No application DDL; Hibernate runs with `validate` |
| Initial storage | Small application schema; 5 GB initial allocation is enough unless local policy requires another size |

## Accounts And Access

### Application runtime account

Please create an Oracle application schema/user for the backend service. The
default name used by the deployment examples is:

```text
MISSION_CONTROL
```

If the DB team uses a different username, the deployment can override it with
`DB_USERNAME`.

The application should connect as the schema owner unless the DB team creates
synonyms or another approved default-schema pattern. The current application
configuration expects unqualified table names in the login schema.

After schema creation, runtime startup should use:

```text
HIBERNATE_DDL_AUTO=validate
```

That means the application validates the schema at startup but does not create
or modify database objects.

### Personal AD / Windows access

Please grant the AD / Windows account below access to log in to the database for
deployment validation and support:

```text
stcmc
```

Requested access for `stcmc`:

- Ability to connect using the organization's standard AD / Windows database
  authentication flow.
- Ability to view the LenseIQ schema metadata.
- Ability to run validation queries against the LenseIQ tables.
- In non-production only, permission to run the provided schema DDL if this is
  allowed by policy. In production, DBAs can run the DDL instead.

Do not send passwords by email. Please provide credentials or wallet details via
the approved enterprise secret-management process.

## Schema Creation

The required DDL is included with the deployment package:

```text
deploy/oracle-schema.sql
```

Preferred execution pattern:

```bash
sqlplus MISSION_CONTROL/<password>@//<oracle-host>:<port>/<service-name> @deploy/oracle-schema.sql
```

If the DB team runs the script from an administrative account, please ensure the
objects are created in the application schema. The script uses unqualified table
names and is intended to be run while connected as the application schema owner.

The schema uses these Oracle data types:

- UUID identifiers: `RAW(16)`
- Boolean values: `NUMBER(1)` with check constraints
- Java `Instant` timestamps: `TIMESTAMP(6) WITH TIME ZONE`
- Text fields: `VARCHAR2(... CHAR)`

Objects created by the script:

- `clusters`
- `cluster_auth_profiles`
- `cluster_listeners`
- `service_endpoints`
- `cluster_health_snapshots`
- `component_health_snapshots`
- `service_accounts`
- `service_account_scopes`
- `service_account_environments`
- `service_account_cluster_ids`
- `service_account_tokens`
- `audit_events`
- `health_refresh_operations`
- `metrics_targets`
- Supporting primary keys, foreign keys, unique constraints, check constraints,
  and indexes.

No stored procedures, triggers, sequences, database links, or scheduled database
jobs are required.

## Runtime Connection Settings

The application will be configured with these environment variables:

```bash
SPRING_PROFILES_ACTIVE=oracle
DB_URL=jdbc:oracle:thin:@//<oracle-host>:<port>/<service-name>
DB_USERNAME=<application-schema-user>
DB_PASSWORD=<provided-through-secret-vault>
HIBERNATE_DDL_AUTO=validate
```

For TCPS / wallet-based access, please provide the enterprise-approved JDBC URL,
wallet location requirements, and any JVM properties required by the platform.

## Network Requirements

Please allow the application host or subnet to connect to the Oracle listener:

```text
<application-host-or-subnet> -> <oracle-host>:<oracle-port>
```

The application only requires outbound JDBC connectivity to Oracle. No inbound
database connection to the application is required.

## Validation Queries

After the schema is created, these checks should succeed:

```sql
SELECT table_name
FROM user_tables
WHERE table_name IN (
  'CLUSTERS',
  'SERVICE_ACCOUNTS',
  'AUDIT_EVENTS',
  'METRICS_TARGETS'
)
ORDER BY table_name;

SELECT COUNT(*) FROM clusters;
SELECT COUNT(*) FROM metrics_targets;
```

The application startup check should also pass with:

```text
HIBERNATE_DDL_AUTO=validate
```

## Information Requested Back From DB Team

Please provide:

- Oracle host, port, and service name.
- Final application schema/user name.
- Confirmation that `deploy/oracle-schema.sql` has been applied.
- Confirmation that AD / Windows account `stcmc` can log in.
- Secret-vault reference or approved handoff method for runtime credentials.
- Any TCPS wallet, truststore, or JDBC connection properties required.
- Any firewall ticket/reference confirming application-to-database connectivity.

## Request Email Template

Subject: Request to provision Oracle schema for LenseIQ / Kafka Mission Control

Hello Oracle DB Team,

Could you please provision an Oracle schema for the LenseIQ / Kafka Mission
Control backend in `<environment>`?

Requested setup:

- Application/schema user: `MISSION_CONTROL` or your approved naming standard.
- Oracle version: 19c or newer preferred.
- Character set: AL32UTF8 preferred.
- Runtime mode: application will use Hibernate `validate`; it will not create or
  modify schema objects at startup.
- DDL: please apply the attached `oracle-schema.sql` as the application schema
  owner.
- Network: allow the application host/subnet `<application-host-or-subnet>` to
  connect to the Oracle listener.

Please also grant my AD / Windows account `stcmc` access to log in to the
database for deployment validation and support. In non-production, it would be
helpful if `stcmc` can run the schema DDL if policy allows. In production,
read/validation access is fine if DBAs need to own all DDL execution.

Please send back the Oracle host, port, service name, final schema username,
confirmation that the DDL has been applied, and the approved secret-vault or
credential handoff reference. Please do not send passwords over email.

Thank you,
Rajeev
