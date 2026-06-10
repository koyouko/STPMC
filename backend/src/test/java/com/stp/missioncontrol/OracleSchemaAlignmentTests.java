package com.stp.missioncontrol;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "app.datasource.table-prefix=STP_Kafka_HC_",
        "app.defaults.seed-demo-data=false",
        "app.defaults.seed-local-dev-cluster=false",
        "app.health.poll-interval-ms=3600000",
        "spring.datasource.url=jdbc:h2:mem:oracle-schema-alignment;MODE=Oracle;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class OracleSchemaAlignmentTests {

    private static final Pattern CREATE_TABLE = Pattern.compile(
            "(?is)CREATE\\s+TABLE\\s+(\\S+)\\s*\\((.*?)\\)\\s*;"
    );
    private static final Pattern COLUMN_LINE = Pattern.compile("^\\s*([A-Za-z][A-Za-z0-9_]*)\\s+.+");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void oracleSchemaTableColumnsMatchHibernateMappings() throws IOException {
        Map<String, Set<String>> oracleSchema = parseOracleSchema();
        Map<String, Set<String>> hibernateSchema = readHibernateCreatedSchema(oracleSchema.keySet());

        assertThat(hibernateSchema).isEqualTo(oracleSchema);
    }

    private static Map<String, Set<String>> parseOracleSchema() throws IOException {
        String schema = Files.readString(Path.of("../deploy/oracle-schema.sql"));
        Matcher tableMatcher = CREATE_TABLE.matcher(schema);
        Map<String, Set<String>> tables = new LinkedHashMap<>();
        while (tableMatcher.find()) {
            String tableName = normalize(tableMatcher.group(1));
            Set<String> columns = new TreeSet<>();
            for (String rawLine : tableMatcher.group(2).split("\\R")) {
                String line = rawLine.strip();
                if (line.isBlank()
                        || line.startsWith("CONSTRAINT ")
                        || line.startsWith("PRIMARY ")
                        || line.startsWith("FOREIGN ")
                        || line.startsWith("UNIQUE ")
                        || line.startsWith("CHECK ")
                        || line.startsWith("REFERENCES ")) {
                    continue;
                }
                Matcher columnMatcher = COLUMN_LINE.matcher(line);
                if (columnMatcher.matches()) {
                    columns.add(normalize(columnMatcher.group(1)));
                }
            }
            tables.put(tableName, columns);
        }
        return tables;
    }

    private Map<String, Set<String>> readHibernateCreatedSchema(Set<String> tableNames) {
        Map<String, Set<String>> tables = new LinkedHashMap<>();
        for (String tableName : tableNames) {
            List<String> columns = jdbcTemplate.queryForList(
                    """
                    SELECT COLUMN_NAME
                    FROM INFORMATION_SCHEMA.COLUMNS
                    WHERE UPPER(TABLE_NAME) = ?
                    ORDER BY ORDINAL_POSITION
                    """,
                    String.class,
                    tableName
            );
            assertThat(columns)
                    .as("Hibernate-created columns for %s", tableName)
                    .isNotEmpty();
            Set<String> normalizedColumns = new TreeSet<>();
            columns.forEach(column -> normalizedColumns.add(normalize(column)));
            tables.put(tableName, normalizedColumns);
        }
        return tables;
    }

    private static String normalize(String name) {
        return name.replace("\"", "").toUpperCase();
    }
}
