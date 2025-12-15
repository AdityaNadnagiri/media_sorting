package com.media.sort.batch.reader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemReader;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Custom ItemReader for reading media files from a source folder.
 * Recursively scans the folder and returns media files one at a time.
 */
public class MediaFileReader implements ItemReader<File> {

    private static final Logger logger = LoggerFactory.getLogger(MediaFileReader.class);

    // Static extension sets for file type detection
    private static final java.util.Set<String> IMAGE_EXTENSIONS = new java.util.HashSet<>(java.util.Arrays.asList(
            "arw", "jpg", "jpeg", "gif", "bmp", "ico", "tif", "tiff", "raw", "indd",
            "ai", "eps", "pdf", "heic", "cr2", "nrw", "k25", "png", "webp"));

    private static final java.util.Set<String> VIDEO_EXTENSIONS = new java.util.HashSet<>(java.util.Arrays.asList(
            "mp4", "mkv", "flv", "avi", "mov", "wmv", "rm", "mpg", "mpeg",
            "3gp", "vob", "m4v", "3g2", "divx", "xvid", "webm"));

    private final String sourceFolder;
    private List<File> mediaFiles;
    private int currentIndex = 0;

    public MediaFileReader(String sourceFolder) {
        this.sourceFolder = sourceFolder;
        this.mediaFiles = new ArrayList<>();
        scanFolder();
    }

    /**
     * Recursively scan the source folder for media files
     */
    private void scanFolder() {
        logger.info("Scanning folder for media files: {}", sourceFolder);
        File directory = new File(sourceFolder);

        if (!directory.exists() || !directory.isDirectory()) {
            logger.error("Source directory does not exist or is not a directory: {}", sourceFolder);
            return;
        }

        scanDirectory(directory);
        logger.info("Found {} media files to process", mediaFiles.size());
    }

    /**
     * Recursively scan a directory and add media files to the list
     */
    private void scanDirectory(File directory) {
        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isFile()) {
                // Check if file is a supported media type
                String extension = getFileExtension(file.getName());
                if (IMAGE_EXTENSIONS.contains(extension) ||
                        VIDEO_EXTENSIONS.contains(extension)) {
                    mediaFiles.add(file);
                }
            } else if (file.isDirectory()) {
                // Skip if this directory or any parent is an output directory
                if (isInsideOutputDirectory(file)) {
                    logger.debug("Skipping directory inside output folder: {}", file.getAbsolutePath());
                    continue; // Don't process anything inside output directories
                }

                // Recursively scan other directories
                scanDirectory(file);
            }
        }
    }

    /**
     * Check if a directory is inside any output directory (Images, Videos, others,
     * EmptyFolder)
     */
    private boolean isInsideOutputDirectory(File directory) {
        File current = directory;
        File sourceDir = new File(sourceFolder);

        while (current != null && !current.equals(sourceDir)) {
            String dirName = current.getName();

            // Check if this is an output directory
            if (dirName.equals("Images") || dirName.equals("Videos") ||
                    dirName.equals("EmptyFolder") || dirName.equals("others")) {

                // Verify it's actually under the source folder
                File parent = current.getParentFile();
                if (parent != null && parent.equals(sourceDir)) {
                    return true; // This directory is inside an output folder
                }
            }

            current = current.getParentFile();
        }

        return false;
    }

    /**
     * Get file extension from filename
     */
    private String getFileExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot > 0 && lastDot < filename.length() - 1) {
            return filename.substring(lastDot + 1).toLowerCase();
        }
        return "";
    }

    @Override
    public File read() {
        if (currentIndex < mediaFiles.size()) {
            File file = mediaFiles.get(currentIndex);
            currentIndex++;
            return file;
        }
        return null; // End of data
    }
}
