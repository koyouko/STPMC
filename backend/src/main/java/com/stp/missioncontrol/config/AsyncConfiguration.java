package com.stp.missioncontrol.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AsyncConfiguration {

    @Bean
    public Executor missionControlTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("mission-control-");
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(12);
        executor.setQueueCapacity(100);
        executor.initialize();
        return executor;
    }

    @Bean(destroyMethod = "shutdown")
    public ExecutorService metricsScraperExecutor() {
        return Executors.newFixedThreadPool(10,
                new CustomizableThreadFactory("metrics-scraper-"));
    }
}
