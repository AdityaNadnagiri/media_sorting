package com.media.sort.batch.reader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemReader;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Custom ItemReader for finding empty folders.
 * Scans directory tree and returns empty folders in bottom-up order.
 */
public class EmptyFolderReader implements ItemReader<File> {

    private static final Logger logger = LoggerFactory.getLogger(EmptyFolderReader.class);

    private final String targetFolder;
    private List<File> emptyFolders;
    private int currentIndex = 0;

    public EmptyFolderReader(String targetFolder) {
        this.targetFolder = targetFolder;
        this.emptyFolders = new ArrayList<>();
        scanForEmptyFolders();
    }

    /**
     * Scan directory tree for empty folders
     */
    private void scanForEmptyFolders() {
        logger.info("Scanning for empty folders in: {}", targetFolder);
        File directory = new File(targetFolder);

        if (!directory.exists() || !directory.isDirectory()) {
            logger.error("Target directory does not exist or is not a directory: {}", targetFolder);
            return;
        }

        findEmptyFolders(directory);

        // Reverse the list to process from deepest to shallowest
        Collections.reverse(emptyFolders);

        logger.info("Found {} empty folders", emptyFolders.size());
    }

    /**
     * Recursively find empty folders
     */
    private void findEmptyFolders(File directory) {
        // Skip the EmptyFolder directory and anything inside it to avoid infinite loop
        Path emptyFolderPath = new File(targetFolder, "EmptyFolder").toPath();
        if (directory.toPath().startsWith(emptyFolderPath)) {
            logger.debug("Skipping EmptyFolder or its subdirectory: {}", directory.getAbsolutePath());
            return;
        }

        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }

        // First, recursively process subdirectories
        for (File file : files) {
            if (file.isDirectory()) {
                findEmptyFolders(file);
            }
        }

        // Then check if this directory is now empty
        files = directory.listFiles();
        if (files != null && files.length == 0) {
            emptyFolders.add(directory);
        }
    }

    @Override
    public File read() {
        if (currentIndex < emptyFolders.size()) {
            File folder = emptyFolders.get(currentIndex);
            currentIndex++;
            return folder;
        }
        return null; // End of data
    }
}
