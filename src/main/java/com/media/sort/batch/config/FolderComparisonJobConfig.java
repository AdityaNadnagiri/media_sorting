package com.media.sort.batch.config;

import com.media.sort.MediaSortingProperties;
import com.media.sort.batch.dto.FileHashDTO;
import com.media.sort.batch.dto.FileMoveDTO;
import com.media.sort.batch.processor.DuplicateFileProcessor;
import com.media.sort.batch.processor.FileHashProcessor;
import com.media.sort.batch.reader.FolderFileReader;
import com.media.sort.batch.writer.FileMoveWriter;
import com.media.sort.batch.writer.HashMapWriter;
import com.media.sort.service.FileQualityComparator;

import com.media.sort.service.ExifDataFactory;
import com.media.sort.service.MediaFileService;
import com.media.sort.service.PerceptualHashService;
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
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enhanced configuration for Folder Comparison Batch Job with quality
 * detection.
 * Now includes perceptual hashing, burst detection, and RAW+JPEG pairing.
 * This job compares two folders, determines which files are higher quality,
 * and organizes them accordingly (originals in main folder, duplicates in
 * Duplicates subfolder).
 * 
 * Job flow:
 * 1. Build hash map from folder2 (secondary/reference folder) with EXIF data
 * and perceptual hash
 * 2. Compare folder1 files against hash map, determine quality, and move files
 */
@Configuration
public class FolderComparisonJobConfig {

    @Autowired
    private MediaSortingProperties properties;

    @Autowired
    private MediaFileService mediaFileService;

    @Autowired
    private FileQualityComparator qualityComparator;

    @Autowired
    private ExifDataFactory exifDataFactory;

    @Autowired
    private PerceptualHashService perceptualHashService;

    /**
     * Shared hash map for storing file hashes from folder2 with metadata
     */
    @Bean
    public ConcurrentHashMap<String, FileHashDTO> folderComparisonHashMap() {
        return new ConcurrentHashMap<>();
    }

    /**
     * Folder Comparison Job
     */
    @Bean
    @SuppressWarnings("null")
    public Job folderComparisonJob(JobRepository jobRepository,
            Step buildHashMapStep,
            Step compareFoldersStep) {
        return new JobBuilder("folderComparisonJob", jobRepository)
                .start(buildHashMapStep)
                .next(compareFoldersStep)
                .build();
    }

    /**
     * Step 1: Build hash map from folder2 (secondary folder) with EXIF data and
     * perceptual hash
     */
    @Bean
    public Step buildHashMapStep(JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            FolderFileReader folder2Reader,
            FileHashProcessor fileHashProcessor,
            HashMapWriter hashMapWriter) {
        return new StepBuilder("buildHashMapStep", jobRepository)
                .<File, FileHashDTO>chunk(10, transactionManager)
                .reader(folder2Reader)
                .processor(fileHashProcessor)
                .writer(hashMapWriter)
                .build();
    }

    /**
     * Step 2: Compare folder1 files, determine quality, and move files
     */
    @Bean
    @SuppressWarnings("null")
    public Step compareFoldersStep(JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            FolderFileReader folder1Reader,
            DuplicateFileProcessor duplicateFileProcessor,
            FileMoveWriter fileMoveWriter) {
        return new StepBuilder("compareFoldersStep", jobRepository)
                .<File, FileMoveDTO>chunk(10, transactionManager)
                .reader(folder1Reader)
                .processor(duplicateFileProcessor)
                .writer(fileMoveWriter)
                .build();
    }

    /**
     * Reader for folder2 (secondary/reference folder)
     */
    @Bean
    @StepScope
    public FolderFileReader folder2Reader(@Value("#{jobParameters['folder2Path']}") String folder2Path) {
        String folder = folder2Path != null ? folder2Path : properties.getBatchJob().getSecondaryFolderPath();
        return new FolderFileReader(folder);
    }

    /**
     * Reader for folder1 (primary folder to check for duplicates)
     */
    @Bean
    @StepScope
    public FolderFileReader folder1Reader(@Value("#{jobParameters['folder1Path']}") String folder1Path) {
        String folder = folder1Path != null ? folder1Path : properties.getBatchJob().getPrimaryFolderPath();
        return new FolderFileReader(folder);
    }

    /**
     * Processor to calculate file hash, extract EXIF data, and compute perceptual
     * hash
     */
    @Bean
    @StepScope
    public FileHashProcessor fileHashProcessor() {
        return new FileHashProcessor(mediaFileService, exifDataFactory, perceptualHashService);
    }

    /**
     * Writer to build hash map from folder2 with metadata
     */
    @Bean
    @StepScope
    public HashMapWriter hashMapWriter(ConcurrentHashMap<String, FileHashDTO> folderComparisonHashMap) {
        return new HashMapWriter(folderComparisonHashMap);
    }

    /**
     * Enhanced processor to identify duplicates, determine quality,
     * with burst detection, RAW+JPEG pairing, and perceptual hashing support
     */
    @Bean
    @StepScope
    public DuplicateFileProcessor duplicateFileProcessor(
            ConcurrentHashMap<String, FileHashDTO> folderComparisonHashMap) {
        return new DuplicateFileProcessor(
                mediaFileService,
                qualityComparator,
                exifDataFactory,
                folderComparisonHashMap,
                perceptualHashService);
    }

    /**
     * Writer to move files based on quality comparison
     */
    @Bean
    @StepScope
    public FileMoveWriter fileMoveWriter() {
        return new FileMoveWriter();
    }
}
