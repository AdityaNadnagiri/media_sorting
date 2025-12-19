package com.media.sort.batch.config;

import com.media.sort.MediaSortingProperties;
import com.media.sort.batch.dto.FileHashDTO;
import com.media.sort.batch.dto.MediaFileDTO;
import com.media.sort.batch.processor.FileHashProcessor;
import com.media.sort.batch.processor.MediaFileProcessor;
import com.media.sort.batch.reader.MediaFileReader;
import com.media.sort.batch.reader.OrganizedFilesReader;
import com.media.sort.batch.writer.HashMapPopulatorWriter;
import com.media.sort.batch.writer.MediaFileWriter;
import com.media.sort.model.ExifData;

import com.media.sort.service.MediaFileService;
import com.media.sort.service.PerceptualHashService;
import com.media.sort.service.ProgressTrackerFactory;
import com.media.sort.service.VideoExifDataService;
import com.media.sort.service.VideoQualityComparator;
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
    @SuppressWarnings("unused") // Used in mediaFileProcessor bean method
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
     * Conditionally includes pre-scan step if cross-run duplicate detection is
     * enabled
     */
    @Bean
    @SuppressWarnings("null")
    public Job mediaOrganizationJob(JobRepository jobRepository,
            Step organizeMediaStep,
            Step preScanOrganizedFilesStep) {

        if (properties.isEnableCrossRunDuplicateDetection()) {
            // Two-step job: pre-scan then organize
            return new JobBuilder("mediaOrganizationJob", jobRepository)
                    .start(preScanOrganizedFilesStep)
                    .next(organizeMediaStep)
                    .build();
        } else {
            // Single-step job: just organize
            return new JobBuilder("mediaOrganizationJob", jobRepository)
                    .start(organizeMediaStep)
                    .build();
        }
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
        String imageExts = String.join(",", properties.getFileExtensions().getSupportedImageExtensions());
        String videoExts = String.join(",", properties.getFileExtensions().getSupportedVideoExtensions());
        return new MediaFileReader(folder, imageExts, videoExts);
    }

    /**
     * Processor - extracts EXIF data and calculates hash
     */
    @Bean
    @StepScope
    public MediaFileProcessor mediaFileProcessor(VideoExifDataService videoExifDataService,
            VideoQualityComparator videoQualityComparator,
            ProgressTrackerFactory progressTrackerFactory,
            PerceptualHashService perceptualHashService) {
        return new MediaFileProcessor(mediaFileService, progressTrackerFactory,
                videoExifDataService, videoQualityComparator, perceptualHashService);
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

    // ===============================================================================
    // CROSS-RUN DUPLICATE DETECTION (Pre-Scan Step)
    // ===============================================================================

    /**
     * Pre-scan step - builds reference hash map from already-organized files
     * This enables duplicate detection across multiple runs
     */
    @Bean
    @SuppressWarnings("null")
    public Step preScanOrganizedFilesStep(JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            OrganizedFilesReader organizedFilesReader,
            FileHashProcessor fileHashProcessor,
            HashMapPopulatorWriter hashMapPopulatorWriter) {
        return new StepBuilder("preScanOrganizedFilesStep", jobRepository)
                .<File, FileHashDTO>chunk(100, transactionManager)
                .reader(organizedFilesReader)
                .processor(fileHashProcessor)
                .writer(hashMapPopulatorWriter)
                .build();
    }

    /**
     * Reader for organized files (Images/Original and Videos/Original)
     */
    @Bean
    @StepScope
    public OrganizedFilesReader organizedFilesReader(@Value("#{jobParameters['sourceFolder']}") String sourceFolder) {
        String folder = sourceFolder != null ? sourceFolder : properties.getSourceFolder();
        return new OrganizedFilesReader(folder);
    }

    /**
     * Processor to calculate hash and extract EXIF for organized files
     */
    @Bean
    @StepScope
    public FileHashProcessor fileHashProcessor(PerceptualHashService perceptualHashService) {
        return new FileHashProcessor(mediaFileService, progressTrackerFactory, perceptualHashService);
    }

    /**
     * Writer to populate the shared hash map with organized files
     */
    @Bean
    @StepScope
    public HashMapPopulatorWriter hashMapPopulatorWriter(Map<String, ExifData> mediaFileHashMap) {
        return new HashMapPopulatorWriter(mediaFileHashMap);
    }
}
