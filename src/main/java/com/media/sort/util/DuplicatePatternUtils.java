package com.media.sort.util;

/**
 * Utility class for detecting and removing OS-generated duplicate patterns from
 * filenames.
 * Centralizes the logic for identifying copy patterns like "(1)", " - Copy",
 * "copy1", etc.
 */
public class DuplicatePatternUtils {

    private DuplicatePatternUtils() {
        // Private constructor to prevent instantiation
    }

    /**
     * Checks if a filename contains OS-generated duplicate patterns.
     * 
     * @param filename The filename to check
     * @return true if the filename contains duplicate patterns, false otherwise
     */
    public static boolean hasOSDuplicatePattern(String filename) {
        if (filename == null) {
            return false;
        }

        // If removing the suffix changes the filename, it had a pattern
        String cleaned = removeNumberedSuffix(filename);
        return !filename.equals(cleaned);
    }

    /**
     * Removes OS-generated duplicate patterns and numbered suffixes from filename.
     * Examples:
     * - "IMG_001_1.jpg" -> "IMG_001.jpg"
     * - "photo_2.png" -> "photo.png"
     * - "ADLZ2152 - Copy.JPG" -> "ADLZ2152.JPG"
     * - "AFBO7949 - Copy (2).JPG" -> "AFBO7949.JPG"
     * - "Photo (1).jpg" -> "Photo.jpg"
     * 
     * @param fileName The filename to clean
     * @return The filename with duplicate patterns removed
     */
    public static String removeNumberedSuffix(String fileName) {
        if (fileName == null) {
            return null;
        }

        int lastDot = fileName.lastIndexOf('.');
        if (lastDot == -1) {
            return fileName; // No extension
        }

        String nameWithoutExt = fileName.substring(0, lastDot);
        String extension = fileName.substring(lastDot);

        // Remove OS duplicate patterns (anchored at end with $)
        // Pattern: " - Copy (2)", " - Copy (123)", etc.
        nameWithoutExt = nameWithoutExt.replaceAll("\\s*-\\s*[Cc]opy\\s*\\(\\d+\\)$", "");

        // Pattern: " - Copy"
        nameWithoutExt = nameWithoutExt.replaceAll("\\s*-\\s*[Cc]opy$", "");

        // Pattern: " copy 1", " copy 2", etc.
        nameWithoutExt = nameWithoutExt.replaceAll("\\s+[Cc]opy\\s+\\d+$", "");

        // Pattern: "copy1", "copy2", etc. (no space)
        nameWithoutExt = nameWithoutExt.replaceAll("[Cc]opy\\d+$", "");

        // Pattern: "_copy_1", "_copy_2", etc.
        nameWithoutExt = nameWithoutExt.replaceAll("_[Cc]opy_\\d+$", "");

        // Pattern: "1copy1", "2copy2", etc.
        nameWithoutExt = nameWithoutExt.replaceAll("\\d+[Cc]opy\\d+$", "");

        // Pattern: " (1)", " (2)", "(123)", etc. - numbering suffix
        nameWithoutExt = nameWithoutExt.replaceAll("\\s*\\(\\d+\\)$", "");

        // Remove common numbered suffixes: _1, _2, etc. (only _1 to _99, not _9515!)
        nameWithoutExt = nameWithoutExt.replaceAll("_\\d{1,2}$", "");

        // Remove _copy, _duplicate suffixes (case insensitive)
        nameWithoutExt = nameWithoutExt.replaceAll("_[Cc]opy$", "");
        nameWithoutExt = nameWithoutExt.replaceAll("_[Dd]uplicate$", "");

        return nameWithoutExt + extension;
    }
}
