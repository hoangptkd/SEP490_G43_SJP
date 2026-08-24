package com.sjp.recruitment.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AiInterviewBackgroundConfiguration {

    public static final String EXECUTOR_BEAN = "aiInterviewBackgroundExecutor";

    @Bean(name = EXECUTOR_BEAN)
    public ThreadPoolTaskExecutor aiInterviewBackgroundExecutor(AiInterviewProperties properties) {
        int workers = Math.max(1, properties.getBackgroundCorePoolSize());
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(workers);
        executor.setMaxPoolSize(workers);
        executor.setQueueCapacity(Math.max(1, properties.getBackgroundQueueCapacity()));
        executor.setThreadNamePrefix("ai-interview-background-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}
