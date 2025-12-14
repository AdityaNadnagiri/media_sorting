package com.media.sort.batch.processor;

import com.media.sort.batch.dto.FileHashDTO;
import com.media.sort.model.ExifData;

import com.media.sort.service.MediaFileService;
import com.media.sort.service.ProgressTrackerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemProcessor;

import java.io.File;

/**
 * ItemProcessor that calculates file hash and extracts EXIF data.
 * Used in folder comparison to build hash map with metadata.
 */
public class FileHashProcessor implements ItemProcessor<File, FileHashDTO> {

    private static final Logger logger = LoggerFactory.getLogger(FileHashProcessor.class);

    private final MediaFileService mediaFileService;
    private final ProgressTrackerFactory progressTrackerFactory;

    public FileHashProcessor(MediaFileService mediaFileService,
            ProgressTrackerFactory progressTrackerFactory) {
        this.mediaFileService = mediaFileService;
        this.progressTrackerFactory = progressTrackerFactory;
    }

    @Override
    public FileHashDTO process(File file) throws Exception {
        try {
            String hash = mediaFileService.calculateHash(file.toPath());

            // Extract EXIF data for media files
            ExifData exifData = null;

            // Try to create ExifData - it will determine if it's a media file
            try {
                exifData = new ExifData(file);

                // Set dependencies
                exifData.setVideoExifDataService(null); // Video service not needed for hash comparison

                // Initialize progress trackers
                if (progressTrackerFactory != null) {
                    exifData.setProgressTrackers(
                            progressTrackerFactory.getImageErrorTracker(),
                            progressTrackerFactory.getFileComparisonTracker(),
                            progressTrackerFactory.getFileComparisonTracker());
                }

                // If it's not a media file, set exifData to null
                if (exifData.isOther()) {
                    exifData = null;
                } else {
                    logger.debug("Extracted EXIF data for: {}", file.getName());
                }
            } catch (Exception e) {
                logger.warn("Failed to extract EXIF data for: {}", file.getName(), e);
                // Continue without EXIF data
                exifData = null;
            }

            return new FileHashDTO(file.toPath(), hash, exifData);

        } catch (Exception e) {
            logger.error("Error processing file: {}", file.getAbsolutePath(), e);
            return null;
        }
    }
}
