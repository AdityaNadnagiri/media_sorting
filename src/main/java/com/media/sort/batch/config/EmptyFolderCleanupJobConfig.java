package com.media.sort.batch.config;

import com.media.sort.MediaSortingProperties;
import com.media.sort.batch.reader.EmptyFolderReader;
import com.media.sort.batch.writer.EmptyFolderWriter;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.File;

/**
 * Configuration for Empty Folder Cleanup Batch Job.
 * This job scans a directory tree and deletes empty folders.
 */
@Configuration
public class EmptyFolderCleanupJobConfig {

    @Autowired
    private MediaSortingProperties properties;

    /**
     * Empty Folder Cleanup Job
     */
    @Bean
    @SuppressWarnings("null")
    public Job emptyFolderCleanupJob(JobRepository jobRepository,
            Step cleanupEmptyFoldersStep) {
        return new JobBuilder("emptyFolderCleanupJob", jobRepository)
                .start(cleanupEmptyFoldersStep)
                .build();
    }

    /**
     * Step to cleanup empty folders
     */
    @Bean
    @SuppressWarnings("null")
    public Step cleanupEmptyFoldersStep(JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            EmptyFolderReader emptyFolderReader,
            EmptyFolderWriter emptyFolderWriter) {
        return new StepBuilder("cleanupEmptyFoldersStep", jobRepository)
                .<File, File>chunk(10, transactionManager)
                .reader(emptyFolderReader)
                .writer(emptyFolderWriter)
                .build();
    }

    /**
     * Reader - scans for empty folders
     */
    @Bean
    @StepScope
    public EmptyFolderReader emptyFolderReader(@Value("#{jobParameters['targetFolder']}") String targetFolder) {
        String folder = targetFolder != null ? targetFolder : properties.getSourceFolder();
        return new EmptyFolderReader(folder);
    }

    /**
     * Writer - moves empty folders to EmptyFolder directory
     */
    @Bean
    @StepScope
    public EmptyFolderWriter emptyFolderWriter(@Value("#{jobParameters['targetFolder']}") String targetFolder) {
        String folder = targetFolder != null ? targetFolder : properties.getSourceFolder();
        return new EmptyFolderWriter(folder);
    }
}
