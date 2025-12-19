package com.media.sort.service;

import com.media.sort.model.ExifData;

import lombok.Getter;
import lombok.Setter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;

/**
 * Service to compare two files and determine which is higher quality.
 * Uses multiple criteria: file size, EXIF date, resolution, etc.
 */
@Service
public class FileQualityComparator {

    private static final Logger logger = LoggerFactory.getLogger(FileQualityComparator.class);

    /**
     * Compare two files and determine which is higher quality.
     * Returns true if file1 is higher quality than file2.
     * 
     * Comparison criteria (in order of priority):
     * 0. **SPECIAL RULE**: If a file has BOTH higher resolution AND larger file
     * size,
     * prioritize it as original regardless of dates (relaxes date rules)
     * 1. Image resolution (higher is better - enables future editing)
     * 2. EXIF date taken (older is original)
     * 3. File modified date (older is original)
     * 4. EXIF date created (older is original)
     * 5. File size (larger is better as tiebreaker)
     * 
     * @param file1 First file to compare
     * @param file2 Second file to compare
     * @param exif1 EXIF data for file1 (can be null)
     * @param exif2 EXIF data for file2 (can be null)
     * @return true if file1 is higher quality/original, false if file2 is better
     */
    public boolean isFile1HigherQuality(File file1, File file2, ExifData exif1, ExifData exif2) {

        // **SPECIAL PRIORITY RULE**: If BOTH higher resolution AND larger file size,
        // prioritize as original regardless of dates
        // This handles cases like resized copies where higher quality is clearly
        // identifiable
        boolean hasResolutionAdvantage = false;
        boolean hasFileSizeAdvantage = false;

        // Check resolution advantage
        if (exif1 != null && exif2 != null) {
            Integer width1 = exif1.getWidth();
            Integer height1 = exif1.getHeight();
            Integer width2 = exif2.getWidth();
            Integer height2 = exif2.getHeight();

            if (width1 != null && height1 != null && width2 != null && height2 != null) {
                long pixels1 = (long) width1 * height1;
                long pixels2 = (long) width2 * height2;

                if (pixels1 != pixels2) {
                    hasResolutionAdvantage = pixels1 > pixels2;
                    logger.debug("Resolution difference: {}x{} ({} MP) vs {}x{} ({} MP)",
                            width1, height1, pixels1 / 1_000_000.0,
                            width2, height2, pixels2 / 1_000_000.0);
                }
            }
        }

        // Check file size advantage
        long size1 = file1.length();
        long size2 = file2.length();

        if (size1 != size2) {
            hasFileSizeAdvantage = size1 > size2;
            logger.debug("File size difference: {} vs {} bytes", size1, size2);
        }

        // If BOTH resolution AND file size are higher, this file is clearly the
        // original
        // Override all date-based rules
        if (hasResolutionAdvantage && hasFileSizeAdvantage) {
            logger.info(
                    "File1 has BOTH higher resolution AND larger file size - prioritizing as original (relaxing date rules)");
            return true;
        } else if (!hasResolutionAdvantage && !hasFileSizeAdvantage &&
                (exif1 != null && exif2 != null) &&
                (exif1.getWidth() != null && exif2.getWidth() != null)) {
            // File2 has both advantages
            logger.info(
                    "File2 has BOTH higher resolution AND larger file size - prioritizing as original (relaxing date rules)");
            return false;
        }

        // 1. **PRIORITY**: Compare image resolution if only one file has advantage
        // Higher resolution is ALWAYS better when sizes differ
        // Even if file size is smaller, higher resolution enables better editing
        if (exif1 != null && exif2 != null) {
            Integer width1 = exif1.getWidth();
            Integer height1 = exif1.getHeight();
            Integer width2 = exif2.getWidth();
            Integer height2 = exif2.getHeight();

            if (width1 != null && height1 != null && width2 != null && height2 != null) {
                long pixels1 = (long) width1 * height1;
                long pixels2 = (long) width2 * height2;

                if (pixels1 != pixels2) {
                    return pixels1 > pixels2; // Higher resolution wins
                }
            }
        }

        // 2. If we have EXIF data, compare Date Taken (older is original)
        if (exif1 != null && exif2 != null) {
            if (exif1.getDateTaken() != null && exif2.getDateTaken() != null) {
                if (!exif1.getDateTaken().equals(exif2.getDateTaken())) {
                    // DateTaken differs - use ExifData's isAfter method
                    // Returns true if exif1 is AFTER exif2, so older is original
                    boolean file1IsNewer = exif1.isAfter(exif2);
                    logger.debug("Date taken difference: {} vs {}",
                            exif1.getDateTaken(), exif2.getDateTaken());
                    return !file1IsNewer; // Older file is the original
                }
                // If Date Taken is identical, fall through to next check
            }
        }

        // 3. Compare file modification times (older is original)
        long modified1 = file1.lastModified();
        long modified2 = file2.lastModified();

        if (modified1 != modified2) {
            logger.debug("File modification time difference: {} vs {}", modified1, modified2);
            // Older file is the original
            return modified1 < modified2;
        }

        // 4. If we have EXIF data, compare Date Created (older is original)
        if (exif1 != null && exif2 != null) {
            if (exif1.getDateCreated() != null && exif2.getDateCreated() != null) {
                if (!exif1.getDateCreated().equals(exif2.getDateCreated())) {
                    // DateCreated differs - calculate comparison directly
                    boolean file1CreatedIsNewer = exif1.getDateCreated().after(exif2.getDateCreated());
                    logger.debug("Date created difference: {} vs {}",
                            exif1.getDateCreated(), exif2.getDateCreated());
                    return !file1CreatedIsNewer; // Older file is the original
                }
            }
        }

        // 5. FINAL TIEBREAKER: Compare file sizes (larger is better)
        if (size1 != size2) {
            logger.debug("File size difference (tiebreaker): {} vs {} bytes", size1, size2);
            return size1 > size2;
        }

        // 6. If all else is equal, prefer file1 (arbitrary but consistent)
        logger.debug("Files are equivalent in quality, defaulting to file1");
        return true;
    }

    /**
     * Determine which file should be kept as original and which as duplicate.
     * 
     * @return ComparisonResult indicating which file is original
     */
    public ComparisonResult compareFiles(File file1, File file2, ExifData exif1, ExifData exif2) {
        boolean file1IsOriginal = isFile1HigherQuality(file1, file2, exif1, exif2);

        ComparisonResult result = new ComparisonResult();
        result.setOriginalFile(file1IsOriginal ? file1 : file2);
        result.setDuplicateFile(file1IsOriginal ? file2 : file1);
        result.setOriginalExif(file1IsOriginal ? exif1 : exif2);
        result.setDuplicateExif(file1IsOriginal ? exif2 : exif1);
        result.setFile1IsOriginal(file1IsOriginal);

        logger.info("Comparison result: {} is original, {} is duplicate",
                result.getOriginalFile().getName(),
                result.getDuplicateFile().getName());

        return result;
    }

    /**
     * Result of file quality comparison
     */
    @Setter
    @Getter
    public static class ComparisonResult {
        private File originalFile;
        private File duplicateFile;
        private ExifData originalExif;
        private ExifData duplicateExif;
        private boolean file1IsOriginal;
    }
}
