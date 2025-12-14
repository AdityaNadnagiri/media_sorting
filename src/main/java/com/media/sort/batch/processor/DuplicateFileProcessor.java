package com.media.sort.batch.processor;

import com.media.sort.batch.dto.FileMoveDTO;
import com.media.sort.batch.dto.FileHashDTO;
import com.media.sort.model.ExifData;
import com.media.sort.service.FileQualityComparator;

import com.media.sort.service.MediaFileService;
import com.media.sort.service.ProgressTrackerFactory;
import com.media.sort.util.FileOperationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemProcessor;

import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enhanced ItemProcessor that checks if a file is a duplicate and determines
 * quality.
 * Compares file size, EXIF data, and dates to determine which is original vs
 * duplicate.
 */
public class DuplicateFileProcessor implements ItemProcessor<File, FileMoveDTO> {

    private static final Logger logger = LoggerFactory.getLogger(DuplicateFileProcessor.class);

    private final MediaFileService mediaFileService;
    private final FileQualityComparator qualityComparator;
    private final ProgressTrackerFactory progressTrackerFactory;
    private final ConcurrentHashMap<String, FileHashDTO> referenceHashMap;

    public DuplicateFileProcessor(MediaFileService mediaFileService,
            FileQualityComparator qualityComparator,
            ProgressTrackerFactory progressTrackerFactory,
            ConcurrentHashMap<String, FileHashDTO> referenceHashMap) {
        this.mediaFileService = mediaFileService;
        this.qualityComparator = qualityComparator;
        this.progressTrackerFactory = progressTrackerFactory;
        this.referenceHashMap = referenceHashMap;
    }

    @Override
    public FileMoveDTO process(File file) throws Exception {
        try {
            String hash = mediaFileService.calculateHash(file.toPath());

            if (referenceHashMap.containsKey(hash)) {
                // Duplicate found - compare quality
                FileHashDTO referenceDTO = referenceHashMap.get(hash);

                logger.info("Duplicate found: {} matches {}",
                        file.getName(), referenceDTO.getFilePath().getFileName());

                // Extract EXIF data for current file if it's a media file
                ExifData sourceExif = extractExifData(file);
                ExifData referenceExif = referenceDTO.getExifData();

                // Compare quality
                FileQualityComparator.ComparisonResult comparison = qualityComparator.compareFiles(
                        file,
                        referenceDTO.getFile(),
                        sourceExif,
                        referenceExif);

                // Determine move strategy based on which is original
                return createMoveDTO(file, referenceDTO, comparison);
            }

            // Not a duplicate
            return null;

        } catch (Exception e) {
            logger.error("Error processing file for duplicates: {}", file.getAbsolutePath(), e);
            return null;
        }
    }

    /**
     * Extract EXIF data from file if it's a media file
     */
    private ExifData extractExifData(File file) {
        try {
            ExifData exifData = new ExifData(file);

            // Set dependencies
            exifData.setVideoExifDataService(null); // Video service not needed for comparison

            if (progressTrackerFactory != null) {
                exifData.setProgressTrackers(
                        progressTrackerFactory.getImageErrorTracker(),
                        progressTrackerFactory.getFileComparisonTracker(),
                        progressTrackerFactory.getFileComparisonTracker());
            }

            // Return null if it's not a media file
            if (exifData.isOther()) {
                return null;
            }

            return exifData;
        } catch (Exception e) {
            logger.warn("Failed to extract EXIF data for: {}", file.getName(), e);
        }

        return null;
    }

    /**
     * Create FileMoveDTO based on quality comparison result
     */
    private FileMoveDTO createMoveDTO(File sourceFile, FileHashDTO referenceDTO,
            FileQualityComparator.ComparisonResult comparison) {

        boolean sourceIsOriginal = comparison.isFile1IsOriginal();

        if (sourceIsOriginal) {
            // Source file is higher quality - it should stay, reference should move to
            // duplicates
            // But since we're processing folder1, we need to move source to replace
            // reference
            // and move reference to duplicates
            logger.info("Source file {} is higher quality than reference {}",
                    sourceFile.getName(), referenceDTO.getFilePath().getFileName());

            // Determine target path: replace reference file location
            Path targetPath = referenceDTO.getFilePath();

            // The reference file will be moved to duplicates by the writer
            FileMoveDTO dto = new FileMoveDTO(
                    sourceFile.toPath(),
                    targetPath,
                    referenceDTO.getFilePath(),
                    true, // source is original
                    comparison.getOriginalExif(),
                    comparison.getDuplicateExif());

            return dto;

        } else {
            // Reference file is higher quality - move source to duplicates folder
            logger.info("Reference file {} is higher quality than source {}",
                    referenceDTO.getFilePath().getFileName(), sourceFile.getName());

            // Determine duplicates folder path based on reference location
            Path referencePath = referenceDTO.getFilePath();
            Path referenceParent = referencePath.getParent();

            // Create "Duplicates" subfolder
            Path duplicatesFolder = referenceParent.resolve("Duplicates");
            Path targetPath = duplicatesFolder.resolve(sourceFile.getName());

            // Find unique filename if needed
            if (targetPath.toFile().exists()) {
                targetPath = FileOperationUtils.findUniqueFileName(targetPath);
            }

            FileMoveDTO dto = new FileMoveDTO(
                    sourceFile.toPath(),
                    targetPath,
                    referencePath,
                    false, // reference is original
                    comparison.getDuplicateExif(),
                    comparison.getOriginalExif());

            return dto;
        }
    }
}
