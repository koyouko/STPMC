package com.stp.missioncontrol.config;

import org.hibernate.boot.model.naming.Identifier;
import org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TablePrefixPhysicalNamingStrategyTests {

    @Test
    void prefixesTableNamesOnly() {
        TablePrefixPhysicalNamingStrategy strategy = new TablePrefixPhysicalNamingStrategy(
                "STP_Kafka_HC_",
                PhysicalNamingStrategyStandardImpl.INSTANCE);

        Identifier tableName = strategy.toPhysicalTableName(Identifier.toIdentifier("audit_events"), null);
        Identifier columnName = strategy.toPhysicalColumnName(Identifier.toIdentifier("created_at"), null);

        assertThat(tableName.getText()).isEqualTo("STP_Kafka_HC_audit_events");
        assertThat(columnName.getText()).isEqualTo("created_at");
    }

    @Test
    void doesNotDoublePrefixTableNames() {
        TablePrefixPhysicalNamingStrategy strategy = new TablePrefixPhysicalNamingStrategy(
                "STP_Kafka_HC_",
                PhysicalNamingStrategyStandardImpl.INSTANCE);

        Identifier tableName = strategy.toPhysicalTableName(Identifier.toIdentifier("STP_Kafka_HC_audit_events"), null);

        assertThat(tableName.getText()).isEqualTo("STP_Kafka_HC_audit_events");
    }
}
