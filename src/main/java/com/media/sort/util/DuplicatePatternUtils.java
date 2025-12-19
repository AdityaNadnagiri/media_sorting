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

    /**
     * Checks if two filenames appear to be part of a burst sequence.
     * Burst sequences have sequential numbers and should not be considered
     * duplicates.
     * Examples:
     * - IMG_0146.JPG and IMG_0147.JPG -> true (sequential)
     * - DSC03215.JPG and DSC03216.JPG -> true (sequential)
     * - IMG_001.JPG and IMG_003.JPG -> false (not sequential)
     * 
     * @param filename1 First filename
     * @param filename2 Second filename
     * @return true if filenames appear to be burst shots
     */
    public static boolean isBurstSequence(String filename1, String filename2) {
        if (filename1 == null || filename2 == null) {
            return false;
        }

        // Extract numeric part from filenames
        String num1 = extractTrailingNumber(filename1);
        String num2 = extractTrailingNumber(filename2);

        if (num1 == null || num2 == null) {
            return false;
        }

        // Check if numbers are sequential
        try {
            int n1 = Integer.parseInt(num1);
            int n2 = Integer.parseInt(num2);
            return Math.abs(n2 - n1) == 1;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Extract trailing number from filename (before extension).
     * Examples:
     * - IMG_0146.JPG -> "0146"
     * - DSC03215.JPG -> "03215"
     * - Photo_ABC.JPG -> null
     */
    private static String extractTrailingNumber(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot == -1) {
            return null;
        }

        String nameWithoutExt = filename.substring(0, lastDot);

        // Find trailing digits
        int i = nameWithoutExt.length() - 1;
        while (i >= 0 && Character.isDigit(nameWithoutExt.charAt(i))) {
            i--;
        }

        if (i >= nameWithoutExt.length() - 1) {
            return null; // No trailing digits
        }

        return nameWithoutExt.substring(i + 1);
    }

    /**
     * Checks if a filename is a RAW image format.
     * Common RAW extensions: CR2, ARW, NEF, DNG, ORF, RAF, RW2, etc.
     */
    public static boolean isRawFormat(String filename) {
        if (filename == null) {
            return false;
        }

        String lower = filename.toLowerCase();
        return lower.endsWith(".cr2") || // Canon
                lower.endsWith(".arw") || // Sony
                lower.endsWith(".nef") || // Nikon
                lower.endsWith(".dng") || // Adobe/Universal
                lower.endsWith(".orf") || // Olympus
                lower.endsWith(".raf") || // Fuji
                lower.endsWith(".rw2") || // Panasonic
                lower.endsWith(".pef") || // Pentax
                lower.endsWith(".srw") || // Samsung
                lower.endsWith(".3fr"); // Hasselblad
    }

    /**
     * Checks if two files are a RAW+JPEG pair (same base name, different
     * extensions).
     * Examples:
     * - IMG_001.CR2 and IMG_001.JPG -> true
     * - DSC_1234.NEF and DSC_1234.JPG -> true
     * - IMG_001.JPG and IMG_002.CR2 -> false
     */
    public static boolean isRawJpegPair(String filename1, String filename2) {
        if (filename1 == null || filename2 == null) {
            return false;
        }

        // Get base names (without extension)
        String base1 = getBaseName(filename1);
        String base2 = getBaseName(filename2);

        if (!base1.equalsIgnoreCase(base2)) {
            return false;
        }

        // One must be RAW, one must be JPEG
        boolean file1IsRaw = isRawFormat(filename1);
        boolean file2IsRaw = isRawFormat(filename2);
        boolean file1IsJpeg = filename1.toLowerCase().endsWith(".jpg") ||
                filename1.toLowerCase().endsWith(".jpeg");
        boolean file2IsJpeg = filename2.toLowerCase().endsWith(".jpg") ||
                filename2.toLowerCase().endsWith(".jpeg");

        return (file1IsRaw && file2IsJpeg) || (file2IsRaw && file1IsJpeg);
    }

    /**
     * Get base name of file without extension.
     */
    private static String getBaseName(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot == -1) {
            return filename;
        }
        return filename.substring(0, lastDot);
    }
}
