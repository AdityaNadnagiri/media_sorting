package com.media.sort.batch.config;

import com.media.sort.MediaSortingProperties;
import com.media.sort.batch.dto.MediaFileDTO;
import com.media.sort.batch.processor.MediaFileProcessor;
import com.media.sort.batch.reader.MediaFileReader;
import com.media.sort.batch.writer.MediaFileWriter;
import com.media.sort.model.ExifData;

import com.media.sort.service.MediaFileService;
import com.media.sort.service.PerceptualHashService;
import com.media.sort.service.ProgressTrackerFactory;
import com.media.sort.service.VideoExifDataService;
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

import java.util.Map;

/**
 * Configuration for Media Organization Batch Job.
 * This job scans a source folder, extracts EXIF data, and organizes media
 * files.
 */
@Configuration
public class MediaOrganizationJobConfig {

    @Autowired
    private MediaSortingProperties properties;

    @Autowired
    private MediaFileService mediaFileService;

    @Autowired
    private ProgressTrackerFactory progressTrackerFactory;

    /**
     * Shared hash map for duplicate detection across the job
     */
    @Bean
    public Map<String, ExifData> mediaFileHashMap() {
        return new java.util.concurrent.ConcurrentHashMap<>();
    }

    /**
     * Media Organization Job
     */
    @Bean
    @SuppressWarnings("null")
    public Job mediaOrganizationJob(JobRepository jobRepository,
            Step organizeMediaStep) {
        return new JobBuilder("mediaOrganizationJob", jobRepository)
                .start(organizeMediaStep)
                .build();
    }

    /**
     * Step to organize media files
     */
    @Bean
    @SuppressWarnings("null")
    public Step organizeMediaStep(JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            MediaFileReader mediaFileReader,
            MediaFileProcessor mediaFileProcessor,
            MediaFileWriter mediaFileWriter) {
        return new StepBuilder("organizeMediaStep", jobRepository)
                .<File, MediaFileDTO>chunk(10, transactionManager)
                .reader(mediaFileReader)
                .processor(mediaFileProcessor)
                .writer(mediaFileWriter)
                .build();
    }

    /**
     * Reader - scans source folder for media files
     */
    @Bean
    @StepScope
    public MediaFileReader mediaFileReader(@Value("#{jobParameters['sourceFolder']}") String sourceFolder) {
        String folder = sourceFolder != null ? sourceFolder : properties.getSourceFolder();
        return new MediaFileReader(folder);
    }

    /**
     * Processor - extracts EXIF data and calculates hash
     */
    @Bean
    @StepScope
    public MediaFileProcessor mediaFileProcessor(VideoExifDataService videoExifDataService,
            ProgressTrackerFactory progressTrackerFactory,
            PerceptualHashService perceptualHashService) {
        return new MediaFileProcessor(mediaFileService, progressTrackerFactory,
                videoExifDataService, perceptualHashService);
    }

    /**
     * Writer - moves files to organized structure
     */
    @Bean
    @StepScope
    public MediaFileWriter mediaFileWriter(@Value("#{jobParameters['sourceFolder']}") String sourceFolder,
            Map<String, ExifData> mediaFileHashMap,
            PerceptualHashService perceptualHashService) {
        String folder = sourceFolder != null ? sourceFolder : properties.getSourceFolder();
        return new MediaFileWriter(mediaFileService, properties, folder, mediaFileHashMap, perceptualHashService);
    }
}
