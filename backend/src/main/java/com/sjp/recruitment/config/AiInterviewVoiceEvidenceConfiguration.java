package com.sjp.recruitment.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AiInterviewVoiceEvidenceConfiguration {

    public static final String EXECUTOR_BEAN = "aiInterviewVoiceEvidenceExecutor";

    @Bean(name = EXECUTOR_BEAN)
    public ThreadPoolTaskExecutor aiInterviewVoiceEvidenceExecutor(AiInterviewProperties properties) {
        int workers = Math.max(1, properties.getVoiceEvidenceCorePoolSize());
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(workers);
        executor.setMaxPoolSize(workers);
        executor.setQueueCapacity(Math.max(0, properties.getVoiceEvidenceQueueCapacity()));
        executor.setThreadNamePrefix("ai-interview-voice-evidence-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}
