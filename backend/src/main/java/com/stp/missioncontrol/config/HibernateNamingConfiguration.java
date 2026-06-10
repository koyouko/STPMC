package com.stp.missioncontrol.config;

import org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy;
import org.hibernate.boot.model.naming.PhysicalNamingStrategy;
import org.hibernate.cfg.AvailableSettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
public class HibernateNamingConfiguration {

    @Bean
    HibernatePropertiesCustomizer tablePrefixPhysicalNamingStrategy(
            @Value("${app.datasource.table-prefix:}") String tablePrefix) {
        return hibernateProperties -> {
            if (!StringUtils.hasText(tablePrefix)) {
                return;
            }
            PhysicalNamingStrategy delegate = resolvePhysicalNamingStrategy(
                    hibernateProperties.get(AvailableSettings.PHYSICAL_NAMING_STRATEGY));
            hibernateProperties.put(
                    AvailableSettings.PHYSICAL_NAMING_STRATEGY,
                    new TablePrefixPhysicalNamingStrategy(tablePrefix, delegate));
        };
    }

    private PhysicalNamingStrategy resolvePhysicalNamingStrategy(Object configuredStrategy) {
        if (configuredStrategy instanceof PhysicalNamingStrategy strategy) {
            return strategy;
        }
        return new CamelCaseToUnderscoresNamingStrategy();
    }
}
