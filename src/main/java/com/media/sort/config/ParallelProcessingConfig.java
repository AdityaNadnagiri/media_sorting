package com.media.sort.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Configuration for parallel processing in Spring Batch
 */
@Configuration
public class ParallelProcessingConfig {

    @Autowired
    private MediaSortingConfig config;

    /**
     * Task executor for parallel file processing
     */
    @Bean(name = "mediaProcessingTaskExecutor")
    public TaskExecutor taskExecutor() {
        if (!config.getProcessing().isParallel()) {
            // Return null to use default single-threaded processing
            return null;
        }

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int threads = config.getProcessing().getThreads();

        executor.setCorePoolSize(threads);
        executor.setMaxPoolSize(threads * 2);
        executor.setQueueCapacity(config.getProcessing().getBatchSize() * 2);
        executor.setThreadNamePrefix("media-processor-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();

        return executor;
    }
}
