package com.stp.missioncontrol;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class OracleSupportConfigurationTests {

    @Test
    void backendIncludesOracleJdbcDriver() throws IOException {
        String pom = read("pom.xml");

        assertThat(pom).contains("<groupId>com.oracle.database.jdbc</groupId>");
        assertThat(pom).contains("<artifactId>ojdbc11</artifactId>");
    }

    @Test
    void oracleProfileDefinesDatasourceAndHibernateSettings() throws IOException {
        String oracleProfile = read("src/main/resources/application-oracle.yml");

        assertThat(oracleProfile).contains("jdbc:oracle:thin:@//");
        assertThat(oracleProfile).contains("STP_KAFKA_HC_MISSION_CONTROL");
        assertThat(oracleProfile).contains("driver-class-name: oracle.jdbc.OracleDriver");
        assertThat(oracleProfile).contains("schema: ${DB_SCHEMA:STP_KAFKA_HC_MISSION_CONTROL}");
        assertThat(oracleProfile).contains("table-prefix: ${DB_TABLE_PREFIX:STP_Kafka_HC_}");
        assertThat(oracleProfile).contains("connection-init-sql: \"ALTER SESSION SET CURRENT_SCHEMA=${app.datasource.schema}\"");
        assertThat(oracleProfile).contains("database-platform: org.hibernate.dialect.OracleDialect");
        assertThat(oracleProfile).contains("table_prefix: ${app.datasource.table-prefix}");
        assertThat(oracleProfile).contains("default_schema: ${app.datasource.schema}");
        assertThat(oracleProfile).contains("ddl-auto: ${HIBERNATE_DDL_AUTO:validate}");
    }

    @Test
    void oracleTablePrefixNamingStrategyIsWiredThroughHibernateCustomizer() throws IOException {
        String namingConfiguration = read("src/main/java/com/stp/missioncontrol/config/HibernateNamingConfiguration.java");

        assertThat(namingConfiguration).contains("AvailableSettings.PHYSICAL_NAMING_STRATEGY");
        assertThat(namingConfiguration).contains("TablePrefixPhysicalNamingStrategy");
        assertThat(namingConfiguration).contains("app.datasource.table-prefix");
    }

    @Test
    void deploymentScriptAutoDetectsOracleJdbcUrls() throws IOException {
        String startScript = read("../deploy/start.sh");

        assertThat(startScript).contains("jdbc:oracle");
        assertThat(startScript).contains("PROFILES=\"${PROFILES:+$PROFILES,}oracle\"");
        assertThat(startScript).contains("Oracle (${DB_URL:-external datasource})");
        assertThat(startScript).contains("DB_SCHEMA");
    }

    @Test
    void oracleSchemaUsesExplicitOracleColumnTypes() throws IOException {
        String oracleSchema = read("../deploy/oracle-schema.sql");

        assertThat(oracleSchema).contains("RAW(16)");
        assertThat(oracleSchema).contains("NUMBER(1)");
        assertThat(oracleSchema).contains("TIMESTAMP(6) WITH TIME ZONE");
        assertThat(oracleSchema).contains("CREATE TABLE STP_Kafka_HC_audit_events");
        assertThat(oracleSchema).contains("CREATE TABLE STP_Kafka_HC_service_account_cluster_ids");
        assertThat(oracleSchema).doesNotContain("CREATE TABLE audit_events");
    }

    @Test
    void operatorDocsDescribeOracleStartupPath() throws IOException {
        String readme = read("../README.md");
        String deployReadme = read("../deploy/README.txt");
        String databaseRequest = read("../deploy/oracle-database-team-request.md");

        assertThat(readme).contains("SPRING_PROFILES_ACTIVE=oracle");
        assertThat(readme).contains("STP_KAFKA_HC_MISSION_CONTROL");
        assertThat(readme).contains("DB_SCHEMA");
        assertThat(readme).contains("DB_TABLE_PREFIX");
        assertThat(readme).contains("jdbc:oracle:thin:@//");
        assertThat(readme).contains("deploy/oracle-capacity-plan.md");
        assertThat(deployReadme).contains("SPRING_PROFILES_ACTIVE=oracle");
        assertThat(deployReadme).contains("STP_KAFKA_HC_MISSION_CONTROL");
        assertThat(deployReadme).contains("DB_SCHEMA");
        assertThat(deployReadme).contains("DB_TABLE_PREFIX");
        assertThat(deployReadme).contains("jdbc:oracle:thin:@//");
        assertThat(deployReadme).contains("oracle-capacity-plan.md");
        assertThat(databaseRequest).contains("oracle-capacity-plan.md");
    }

    @Test
    void oracleCapacityPlanDocumentsGrowthDriversAndForecasts() throws IOException {
        String capacityPlan = read("../deploy/oracle-capacity-plan.md");

        assertThat(capacityPlan).contains("STP_Kafka_HC_health_refresh_operations");
        assertThat(capacityPlan).contains("STP_Kafka_HC_audit_events");
        assertThat(capacityPlan).contains("60 seconds");
        assertThat(capacityPlan).contains("Expected production");
        assertThat(capacityPlan).contains("633 MB");
        assertThat(capacityPlan).contains("DB_POOL_SIZE=10");
    }

    private static String read(String path) throws IOException {
        return Files.readString(Path.of(path));
    }
}
