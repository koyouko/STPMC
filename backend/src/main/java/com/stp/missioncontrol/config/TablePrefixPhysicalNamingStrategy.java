package com.stp.missioncontrol.config;

import org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy;
import org.hibernate.boot.model.naming.Identifier;
import org.hibernate.boot.model.naming.PhysicalNamingStrategy;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;
import org.springframework.util.StringUtils;

public class TablePrefixPhysicalNamingStrategy implements PhysicalNamingStrategy {

    private final String tablePrefix;
    private final PhysicalNamingStrategy delegate;

    public TablePrefixPhysicalNamingStrategy(String tablePrefix) {
        this(tablePrefix, new CamelCaseToUnderscoresNamingStrategy());
    }

    public TablePrefixPhysicalNamingStrategy(String tablePrefix, PhysicalNamingStrategy delegate) {
        this.tablePrefix = tablePrefix == null ? "" : tablePrefix;
        this.delegate = delegate == null ? new CamelCaseToUnderscoresNamingStrategy() : delegate;
    }

    @Override
    public Identifier toPhysicalCatalogName(Identifier name, JdbcEnvironment jdbcEnvironment) {
        return delegate.toPhysicalCatalogName(name, jdbcEnvironment);
    }

    @Override
    public Identifier toPhysicalSchemaName(Identifier name, JdbcEnvironment jdbcEnvironment) {
        return delegate.toPhysicalSchemaName(name, jdbcEnvironment);
    }

    @Override
    public Identifier toPhysicalTableName(Identifier name, JdbcEnvironment jdbcEnvironment) {
        Identifier physicalName = delegate.toPhysicalTableName(name, jdbcEnvironment);
        if (physicalName == null || !StringUtils.hasText(tablePrefix)) {
            return physicalName;
        }

        String tableName = physicalName.getText();
        if (tableName.regionMatches(true, 0, tablePrefix, 0, tablePrefix.length())) {
            return physicalName;
        }

        return Identifier.toIdentifier(tablePrefix + tableName, physicalName.isQuoted());
    }

    @Override
    public Identifier toPhysicalSequenceName(Identifier name, JdbcEnvironment jdbcEnvironment) {
        return delegate.toPhysicalSequenceName(name, jdbcEnvironment);
    }

    @Override
    public Identifier toPhysicalColumnName(Identifier name, JdbcEnvironment jdbcEnvironment) {
        return delegate.toPhysicalColumnName(name, jdbcEnvironment);
    }
}
