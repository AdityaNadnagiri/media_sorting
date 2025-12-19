package com.media.sort.batch.reader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemReader;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * ItemReader for media files.
 * Recursively scans a source folder for image and video files.
 * Extensions are loaded from properties via constructor injection.
 */
public class MediaFileReader implements ItemReader<File> {

    private static final Logger logger = LoggerFactory.getLogger(MediaFileReader.class);

    private final String sourceFolder;
    private final Set<String> imageExtensions;
    private final Set<String> videoExtensions;
    private List<File> mediaFiles;
    private int currentIndex = 0;

    public MediaFileReader(String sourceFolder, String imageExtensionsConfig, String videoExtensionsConfig) {
        this.sourceFolder = sourceFolder;
        this.imageExtensions = parseExtensions(imageExtensionsConfig);
        this.videoExtensions = parseExtensions(videoExtensionsConfig);
        this.mediaFiles = new ArrayList<>();
        scanFolder();
    }

    /**
     * Parse comma-separated extension list
     */
    private Set<String> parseExtensions(String extensionsConfig) {
        if (extensionsConfig == null || extensionsConfig.isEmpty()) {
            return new HashSet<>();
        }
        return new HashSet<>(Arrays.asList(extensionsConfig.split(",")));
    }

    /**
     * Scan source folder for media files
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
                if (imageExtensions.contains(extension) || videoExtensions.contains(extension)) {
                    mediaFiles.add(file);
                }
            } else if (file.isDirectory()) {
                // Skip output directories
                if (isInsideOutputDirectory(file)) {
                    logger.debug("Skipping output directory: {}", file.getAbsolutePath());
                    continue;
                }
                // Recursively scan subdirectories
                scanDirectory(file);
            }
        }
    }

    /**
     * Check if a directory is inside any output directory
     */
    private boolean isInsideOutputDirectory(File directory) {
        File current = directory;
        File sourceDir = new File(sourceFolder);

        while (current != null && !current.equals(sourceDir)) {
            String dirName = current.getName();

            if (dirName.equals("Images") || dirName.equals("Videos") ||
                    dirName.equals("EmptyFolder") || dirName.equals("others")) {
                File parent = current.getParentFile();
                if (parent != null && parent.equals(sourceDir)) {
                    return true;
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
