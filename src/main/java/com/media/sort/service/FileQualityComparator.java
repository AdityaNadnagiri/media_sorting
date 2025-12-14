package com.media.sort.service;

import com.media.sort.model.ExifData;
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
     * 1. File size (larger is better for media files)
     * 2. EXIF date taken (older is original)
     * 3. File creation date (older is original)
     * 
     * @param file1 First file to compare
     * @param file2 Second file to compare
     * @param exif1 EXIF data for file1 (can be null)
     * @param exif2 EXIF data for file2 (can be null)
     * @return true if file1 is higher quality/original, false if file2 is better
     */
    public boolean isFile1HigherQuality(File file1, File file2, ExifData exif1, ExifData exif2) {

        // 1. Compare file sizes - larger file is typically higher quality
        long size1 = file1.length();
        long size2 = file2.length();

        if (size1 != size2) {
            double sizeDifference = Math.abs(size1 - size2) / (double) Math.max(size1, size2);
            // If size difference is more than 5%, use size as determining factor
            if (sizeDifference > 0.05) {
                logger.debug("File size difference: {} vs {} bytes", size1, size2);
                return size1 > size2;
            }
        }

        // 2. If we have EXIF data, compare dates (older is original)
        if (exif1 != null && exif2 != null) {
            // Use ExifData's isAfter method - returns true if exif1 is AFTER exif2
            // So if exif1 is after exif2, then exif2 is older (original)
            boolean file1IsNewer = exif1.isAfter(exif2);

            if (exif1.getDateTaken() != null && exif2.getDateTaken() != null) {
                if (!exif1.getDateTaken().equals(exif2.getDateTaken())) {
                    logger.debug("Date taken difference: {} vs {}",
                            exif1.getDateTaken(), exif2.getDateTaken());
                    // Older file is the original
                    return !file1IsNewer;
                }
            }

            // If dates are equal or missing, check creation dates
            if (exif1.getDateCreated() != null && exif2.getDateCreated() != null) {
                if (!exif1.getDateCreated().equals(exif2.getDateCreated())) {
                    logger.debug("Date created difference: {} vs {}",
                            exif1.getDateCreated(), exif2.getDateCreated());
                    return !file1IsNewer;
                }
            }
        }

        // 3. Fallback: compare file modification times (older is original)
        long modified1 = file1.lastModified();
        long modified2 = file2.lastModified();

        if (modified1 != modified2) {
            logger.debug("File modification time difference: {} vs {}", modified1, modified2);
            // Older file is the original
            return modified1 < modified2;
        }

        // 4. If all else is equal, prefer file1 (arbitrary but consistent)
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
    public static class ComparisonResult {
        private File originalFile;
        private File duplicateFile;
        private ExifData originalExif;
        private ExifData duplicateExif;
        private boolean file1IsOriginal;

        public File getOriginalFile() {
            return originalFile;
        }

        public void setOriginalFile(File originalFile) {
            this.originalFile = originalFile;
        }

        public File getDuplicateFile() {
            return duplicateFile;
        }

        public void setDuplicateFile(File duplicateFile) {
            this.duplicateFile = duplicateFile;
        }

        public ExifData getOriginalExif() {
            return originalExif;
        }

        public void setOriginalExif(ExifData originalExif) {
            this.originalExif = originalExif;
        }

        public ExifData getDuplicateExif() {
            return duplicateExif;
        }

        public void setDuplicateExif(ExifData duplicateExif) {
            this.duplicateExif = duplicateExif;
        }

        public boolean isFile1IsOriginal() {
            return file1IsOriginal;
        }

        public void setFile1IsOriginal(boolean file1IsOriginal) {
            this.file1IsOriginal = file1IsOriginal;
        }
    }
}
