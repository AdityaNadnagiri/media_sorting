package com.media.sort.service;

import com.media.sort.MediaSortingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@Service
public class FolderComparisonService {

    private static final Logger logger = LoggerFactory.getLogger(FolderComparisonService.class);

    @Autowired
    private MediaSortingProperties properties;

    @Autowired
    private ProgressTracker progressTracker;
    
    @Autowired
    private MediaFileService mediaFileService;

    private final ConcurrentHashMap<String, Path> folder2Files = new ConcurrentHashMap<>();

    /**
     * Main method to compare two folders and move duplicate files
     * @return ComparisonResult with statistics
     */
    public ComparisonResult compareAndMoveFiles() {
        ComparisonResult result = new ComparisonResult();
        ExecutorService executor = null;

        try {
            logger.info("Starting folder comparison between {} and {}", 
                properties.getBatchJob().getPrimaryFolderPath(), 
                properties.getBatchJob().getSecondaryFolderPath());

            executor = Executors.newFixedThreadPool(properties.getBatchJob().getMaxThreadPoolSize());

            // Create log directories
            createLogDirectories();

            // Process folder2 files to build hash map
            result.setFolder2ProcessedFiles(processFolder2Files());
            logger.info("Processed {} files from folder2", result.getFolder2ProcessedFiles());

            // Process folder1 files and compare/move
            result = processFolder1Files(result);
            logger.info("Processed {} files from folder1, moved {} files", 
                result.getFolder1ProcessedFiles(), result.getMovedFiles());

            result.setStatus("SUCCESS");
            result.setMessage("Folder comparison completed successfully");

        } catch (Exception e) {
            logger.error("Error during folder comparison", e);
            result.setStatus("ERROR");
            result.setMessage("Error occurred: " + e.getMessage());
            progressTracker.saveProgress("ERROR - FolderComparisonService.compareAndMoveFiles: " + e.getMessage());
        } finally {
            shutdownExecutor(executor);
        }

        return result;
    }

    private void createLogDirectories() throws IOException {
        String compareLogsPath = properties.getBatchJob().getComparisonLogsDirectoryPath();
        Files.createDirectories(Paths.get(compareLogsPath));
    }

    private int processFolder2Files() throws IOException {
        int processedCount = 0;
        String folder2Path = properties.getBatchJob().getSecondaryFolderPath();
        
        logger.info("Building hash map from folder2: {}", folder2Path);

        try (Stream<Path> paths = Files.walk(Paths.get(folder2Path))) {
            processedCount = (int) paths.parallel()
                .filter(Files::isRegularFile)
                .peek(this::addToMap)
                .count();
        } catch (IOException e) {
            logger.error("Error processing folder2 files", e);
            progressTracker.saveProgress("ERROR - processFolder2Files: " + e.getMessage());
            throw e;
        }

        return processedCount;
    }

    private ComparisonResult processFolder1Files(ComparisonResult result) throws IOException {
        String folder1Path = properties.getBatchJob().getPrimaryFolderPath();
        
        logger.info("Comparing and moving files from folder1: {}", folder1Path);

        try (Stream<Path> paths = Files.walk(Paths.get(folder1Path))) {
            paths.parallel()
                .filter(Files::isRegularFile)
                .forEach(file -> {
                    try {
                        compareAndMove(file, result);
                        result.incrementFolder1ProcessedFiles();
                        progressTracker.saveProgress("folder1_progress: " + file.toString());
                    } catch (Exception e) {
                        logger.error("Error processing file: {}", file, e);
                        progressTracker.saveProgress("ERROR - compareAndMove: Error processing " + file + ": " + e.getMessage());
                    }
                });
        } catch (IOException e) {
            logger.error("Error processing folder1 files", e);
            progressTracker.saveProgress("ERROR - processFolder1Files: " + e.getMessage());
            throw e;
        }

        return result;
    }

    private void addToMap(Path file) {
        try {
            String fileHash = mediaFileService.calculateHash(file);
            folder2Files.put(fileHash, file);
            progressTracker.saveProgress("folder2_progress: " + file + ":" + fileHash);
        } catch (NoSuchAlgorithmException | IOException e) {
            logger.error("Error calculating hash for file: {}", file, e);
            progressTracker.saveProgress("ERROR - addToMap: Error calculating hash for " + file + ": " + e.getMessage());
        }
    }

    private void compareAndMove(Path file, ComparisonResult result) {
        try {
            String fileHash = mediaFileService.calculateHash(file);
            progressTracker.saveProgress("folder1_progress: " + file.toString() + ":" + fileHash);
            
            Path folder2File = folder2Files.get(fileHash);
            if (folder2File != null) {
                moveFileToOrganizedStructure(file, folder2File, result);
                result.incrementMovedFiles();
            }
        } catch (NoSuchAlgorithmException | IOException e) {
            logger.error("Error comparing and moving file: {}", file, e);
            progressTracker.saveProgress("ERROR - compareAndMove: Error processing " + file + ": " + e.getMessage());
        }
    }

    private void moveFileToOrganizedStructure(Path sourceFile, Path referenceFile, ComparisonResult result) throws IOException {
        String folder1Path = properties.getBatchJob().getPrimaryFolderPath();
        
        // Create target directory based on reference file structure
        Path targetDir = Paths.get(folder1Path, referenceFile.subpath(1, referenceFile.getNameCount() - 1).toString());
        Files.createDirectories(targetDir);
        
        Path destinationPath = targetDir.resolve(sourceFile.getFileName());
        
        if (Files.exists(sourceFile)) {
            try {
                Path uniqueDestination = MediaFileService.findUniqueFileName(destinationPath);
                Files.move(sourceFile, uniqueDestination, StandardCopyOption.REPLACE_EXISTING);
                progressTracker.saveProgress("move_progress: " + sourceFile.toString() + " -> " + uniqueDestination.toString());
                logger.debug("Moved file: {} -> {}", sourceFile, uniqueDestination);
            } catch (IOException e) {
                logger.error("Error moving file from {} to {}", sourceFile, destinationPath, e);
                progressTracker.saveProgress("ERROR - moveFile: Error moving " + sourceFile + " to " + destinationPath + ": " + e.getMessage());
                throw e;
            }
        } else {
            String errorMsg = "Source file does not exist: " + sourceFile;
            logger.warn(errorMsg);
            progressTracker.saveProgress("ERROR - moveFile: " + errorMsg);
        }
    }

    private void shutdownExecutor(ExecutorService executor) {
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                    if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                        logger.warn("Executor did not terminate gracefully");
                    }
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
                logger.warn("Executor shutdown interrupted");
            }
        }
    }

    /**
     * Result class to hold comparison statistics
     */
    public static class ComparisonResult {
        private String status;
        private String message;
        private int folder1ProcessedFiles = 0;
        private int folder2ProcessedFiles = 0;
        private int movedFiles = 0;

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public int getFolder1ProcessedFiles() {
            return folder1ProcessedFiles;
        }

        public void setFolder1ProcessedFiles(int folder1ProcessedFiles) {
            this.folder1ProcessedFiles = folder1ProcessedFiles;
        }

        public synchronized void incrementFolder1ProcessedFiles() {
            this.folder1ProcessedFiles++;
        }

        public int getFolder2ProcessedFiles() {
            return folder2ProcessedFiles;
        }

        public void setFolder2ProcessedFiles(int folder2ProcessedFiles) {
            this.folder2ProcessedFiles = folder2ProcessedFiles;
        }

        public int getMovedFiles() {
            return movedFiles;
        }

        public void setMovedFiles(int movedFiles) {
            this.movedFiles = movedFiles;
        }

        public synchronized void incrementMovedFiles() {
            this.movedFiles++;
        }
    }
}