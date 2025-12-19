package com.media.sort.batch.reader;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemReader;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * ItemReader that scans already-organized directories (Images/Original and
 * Videos/Original)
 * to build a reference hash map for cross-run duplicate detection.
 * 
 * This reader enables detecting duplicates between new files and files that
 * were
 * organized in previous runs.
 */
@Slf4j
public class OrganizedFilesReader implements ItemReader<File> {

    private final String sourceFolder;
    private final List<File> organizedFiles;
    private int currentIndex = 0;

    public OrganizedFilesReader(String sourceFolder) {
        this.sourceFolder = sourceFolder;
        this.organizedFiles = new ArrayList<>();
        scanOrganizedDirectories();
    }

    /**
     * Scan Images/Original and Videos/Original directories for all media files
     */
    private void scanOrganizedDirectories() {
        log.info("Pre-scanning organized directories for duplicate detection...");

        // Scan Images/Original/
        File imageOriginalDir = new File(sourceFolder, "Images/Original");
        if (imageOriginalDir.exists() && imageOriginalDir.isDirectory()) {
            log.info("Scanning: {}", imageOriginalDir.getAbsolutePath());
            scanDirectory(imageOriginalDir);
        }

        // Scan Videos/Original/
        File videoOriginalDir = new File(sourceFolder, "Videos/Original");
        if (videoOriginalDir.exists() && videoOriginalDir.isDirectory()) {
            log.info("Scanning: {}", videoOriginalDir.getAbsolutePath());
            scanDirectory(videoOriginalDir);
        }

        log.info("Pre-scan complete: Found {} organized files to reference", organizedFiles.size());
    }

    /**
     * Recursively scan a directory and collect all files
     */
    private void scanDirectory(File directory) {
        try (Stream<Path> paths = Files.walk(directory.toPath())) {
            paths.filter(Files::isRegularFile)
                    .forEach(path -> organizedFiles.add(path.toFile()));
        } catch (Exception e) {
            log.error("Error scanning organized directory: {}", directory.getAbsolutePath(), e);
        }
    }

    @Override
    public File read() {
        if (currentIndex < organizedFiles.size()) {
            File file = organizedFiles.get(currentIndex);
            currentIndex++;
            return file;
        }
        return null; // End of data
    }

    /**
     * Get the total number of organized files found
     */
    public int getFileCount() {
        return organizedFiles.size();
    }
}
