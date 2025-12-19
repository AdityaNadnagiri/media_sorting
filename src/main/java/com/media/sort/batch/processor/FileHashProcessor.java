package com.media.sort.batch.processor;

import com.media.sort.batch.dto.FileHashDTO;
import com.media.sort.model.ExifData;
import com.media.sort.service.MediaFileService;
import com.media.sort.service.PerceptualHashService;
import com.media.sort.service.ProgressTrackerFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemProcessor;

import java.io.File;

/**
 * Enhanced ItemProcessor that calculates file hash, extracts EXIF data, and
 * computes perceptual hash.
 * Used in folder comparison to build hash map with metadata.
 */
@Slf4j
@RequiredArgsConstructor
public class FileHashProcessor implements ItemProcessor<File, FileHashDTO> {

    private final MediaFileService mediaFileService;
    private final ProgressTrackerFactory progressTrackerFactory;
    private final PerceptualHashService perceptualHashService;

    @Override
    public FileHashDTO process(File file) throws Exception {
        try {
            String hash = mediaFileService.calculateHash(file.toPath());

            // Extract EXIF data for media files
            ExifData exifData = null;
            String perceptualHash = null;

            // Try to create ExifData - it will determine if it's a media file
            try {
                exifData = new ExifData(file);

                // Set dependencies
                exifData.setVideoExifDataService(null); // Video service not needed for hash comparison
                exifData.setVideoQualityComparator(null); // Quality comparator not needed for hash comparison

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
                    log.debug("Extracted EXIF data for: {}", file.getAbsolutePath());

                    // Calculate perceptual hash for images
                    if (exifData.isImage() && perceptualHashService != null) {
                        try {
                            perceptualHash = perceptualHashService.computeHash(file);
                            log.debug("Calculated perceptual hash for: {}", file.getAbsolutePath());
                        } catch (Exception e) {
                            log.warn("Failed to calculate perceptual hash for: {}", file.getAbsolutePath(), e);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to extract EXIF data for: {}", file.getAbsolutePath(), e);
                // Continue without EXIF data
                exifData = null;
            }

            return FileHashDTO.builder()
                    .filePath(file.toPath())
                    .hash(hash)
                    .exifData(exifData)
                    .perceptualHash(perceptualHash)
                    .build();

        } catch (Exception e) {
            log.error("Error processing file: {}", file.getAbsolutePath(), e);
            return null;
        }
    }
}
