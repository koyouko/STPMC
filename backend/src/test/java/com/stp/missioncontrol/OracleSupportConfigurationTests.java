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
        assertThat(oracleProfile).contains("driver-class-name: oracle.jdbc.OracleDriver");
        assertThat(oracleProfile).contains("database-platform: org.hibernate.dialect.OracleDialect");
        assertThat(oracleProfile).contains("ddl-auto: ${HIBERNATE_DDL_AUTO:validate}");
    }

    @Test
    void deploymentScriptAutoDetectsOracleJdbcUrls() throws IOException {
        String startScript = read("../deploy/start.sh");

        assertThat(startScript).contains("jdbc:oracle");
        assertThat(startScript).contains("PROFILES=\"${PROFILES:+$PROFILES,}oracle\"");
        assertThat(startScript).contains("Oracle (${DB_URL:-external datasource})");
    }

    @Test
    void oracleSchemaUsesExplicitOracleColumnTypes() throws IOException {
        String oracleSchema = read("../deploy/oracle-schema.sql");

        assertThat(oracleSchema).contains("RAW(16)");
        assertThat(oracleSchema).contains("NUMBER(1)");
        assertThat(oracleSchema).contains("TIMESTAMP(6) WITH TIME ZONE");
        assertThat(oracleSchema).contains("CREATE TABLE service_account_cluster_ids");
    }

    @Test
    void operatorDocsDescribeOracleStartupPath() throws IOException {
        String readme = read("../README.md");
        String deployReadme = read("../deploy/README.txt");

        assertThat(readme).contains("SPRING_PROFILES_ACTIVE=oracle");
        assertThat(readme).contains("jdbc:oracle:thin:@//");
        assertThat(deployReadme).contains("SPRING_PROFILES_ACTIVE=oracle");
        assertThat(deployReadme).contains("jdbc:oracle:thin:@//");
    }

    private static String read(String path) throws IOException {
        return Files.readString(Path.of(path));
    }
}
