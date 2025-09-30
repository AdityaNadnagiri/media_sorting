package com.media.sort.service;

import com.media.sort.MediaSortingProperties;
import com.media.sort.model.ExifData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Service
public class PhotoOrganizerService {

    private static final Logger logger = LoggerFactory.getLogger(PhotoOrganizerService.class);
    
    private ProgressTracker poErrorTracker;
    private final Map<String, ExifData> fileHash = new HashMap<>();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    
    @Autowired
    private MediaSortingProperties properties;
    
    @Autowired
    private MediaFileService mediaFileService;
    
    @Autowired
    private EmptyFolderCleanupService emptyFolderCleanupService;
    
    @Autowired
    private ProgressTrackerFactory progressTrackerFactory;

    private File emptyFolderDirectory;
    private File duplicateImageDirectory;
    private File originalImageDirectory;
    private File duplicateVideoDirectory;
    private File originalVideoDirectory;
    private String othersDirectory;

    /**
     * Initialize progress tracker after Spring injection
     */
    public void initializeProgressTracker() {
        if (progressTrackerFactory != null && poErrorTracker == null) {
            this.poErrorTracker = progressTrackerFactory.getPhotoOrganizerErrorTracker();
        }
    }
    
    public void initializeDirectories(String sourceFolder) {
        this.emptyFolderDirectory = new File(sourceFolder, properties.getDirectoryStructure().getEmptyFolderDirectoryName());
        this.duplicateImageDirectory = new File(sourceFolder, 
            properties.getDirectoryStructure().getImagesDirectoryName() + "/" + 
            properties.getDirectoryStructure().getDuplicateSubDirectoryName());
        this.originalImageDirectory = new File(sourceFolder, 
            properties.getDirectoryStructure().getImagesDirectoryName() + "/" + 
            properties.getDirectoryStructure().getOriginalSubDirectoryName());
        this.duplicateVideoDirectory = new File(sourceFolder, 
            properties.getDirectoryStructure().getVideosDirectoryName() + "/" + 
            properties.getDirectoryStructure().getDuplicateSubDirectoryName());
        this.originalVideoDirectory = new File(sourceFolder, 
            properties.getDirectoryStructure().getVideosDirectoryName() + "/" + 
            properties.getDirectoryStructure().getOriginalSubDirectoryName());
        this.othersDirectory = sourceFolder + "/" + properties.getDirectoryStructure().getOthersDirectoryName();
    }

    public void organizePhotos(String sourceFolder) {
        initializeProgressTracker(); // Ensure progress tracker is initialized
        logger.info("Starting photo organization for folder: {}", sourceFolder);
        initializeDirectories(sourceFolder);
        
        File sourceDirectory = new File(sourceFolder);
        if (!sourceDirectory.exists() || !sourceDirectory.isDirectory()) {
            logger.error("Source directory does not exist or is not a directory: {}", sourceFolder);
            poErrorTracker.saveProgress("Not directory: " + sourceFolder);
            return;
        }
        
        File[] files = sourceDirectory.listFiles();
        MediaFileService.createDirectory(emptyFolderDirectory);
        
        if (files == null) {
            logger.warn("Empty directory: {}", sourceFolder);
            poErrorTracker.saveProgress("Empty directory: " + sourceFolder);
            return;
        }
        
        for (File file : files) {
            if (file.isFile()) {
                ExifData fileData = new ExifData(file);
                // Initialize progress trackers from factory to avoid hardcoded paths
                if (progressTrackerFactory != null) {
                    fileData.setProgressTrackers(
                        progressTrackerFactory.getImageErrorTracker(),
                        progressTrackerFactory.getFileComparisonTracker(),
                        progressTrackerFactory.getFileComparisonTracker()
                    );
                }
                if (!fileData.isOther()) {
                    mediaFileService.processFile(fileData, this);
                } else {
                    mediaFileService.executeMove(fileData, new File(othersDirectory));
                }
            } else if (file.isDirectory()) {
                if (Objects.requireNonNull(file.listFiles()).length == 0) {
                    moveEmptyFolders(file);
                } else {
                    organizePhotos(file.getAbsolutePath());
                }
            }
        }
        
        // Clean up empty folders using the enhanced service
        logger.info("Starting enhanced empty folder cleanup...");
        EmptyFolderCleanupService.CleanupResult result = emptyFolderCleanupService.deleteEmptyFolders(sourceFolder);
        logger.info("Empty folder cleanup completed: {}", result);
        
        logger.info("Completed photo organization for folder: {}", sourceFolder);
    }

    public void moveImageOrVideoFile(ExifData fileData, String key) {
        boolean isImage = fileData.isImage();
        String folderDate = fileData.getFolderDate();

        if (fileHash.containsKey(key)) {
            // File with the same hash - it's a duplicate
            ExifData originalFileData = fileHash.get(key);
            folderDate = getNewFolderDateForDuplicates(fileData, originalFileData);
            boolean isAfter = fileData.isAfter(originalFileData);
            
            if (isImage) {
                if (isAfter) {
                    mediaFileService.executeMove(fileData, new File(duplicateImageDirectory, originalFileData.getFolderDate()));
                } else {
                    mediaFileService.executeMove(originalFileData, new File(duplicateImageDirectory, folderDate));
                    mediaFileService.executeMove(fileData, new File(originalImageDirectory, folderDate));
                    fileHash.put(key, fileData);
                }
            } else {
                if (isAfter) {
                    mediaFileService.executeMove(fileData, new File(duplicateVideoDirectory, originalFileData.getFolderDate()));
                } else {
                    mediaFileService.executeMove(originalFileData, new File(duplicateVideoDirectory, folderDate));
                    mediaFileService.executeMove(fileData, new File(originalVideoDirectory, folderDate));
                    fileHash.put(key, fileData);
                }
            }
        } else {
            // First occurrence of this file
            if (isImage) {
                mediaFileService.executeMove(fileData, new File(originalImageDirectory, folderDate));
            } else {
                mediaFileService.executeMove(fileData, new File(originalVideoDirectory, folderDate));
            }
            fileHash.put(key, fileData);
        }
    }

    private String getNewFolderDateForDuplicates(ExifData fileData, ExifData existingFileData) {
        try {
            if (dateFormat.parse(fileData.getFolderDate()).after(dateFormat.parse(existingFileData.getFolderDate()))) {
                return existingFileData.getFolderDate();
            }
        } catch (ParseException e) {
            logger.error("Failed to parse folder dates for duplicates comparison", e);
            poErrorTracker.saveProgress("getNewFolderDateForDuplicates file: " + fileData.getFile().getAbsolutePath());
        }
        return fileData.getFolderDate();
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void moveEmptyFolders(File folder) {
        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    moveEmptyFolders(file);
                }
            }
        }
        if (folder.isDirectory() && Objects.requireNonNull(folder.list()).length == 0) {
            boolean moved = folder.renameTo(new File(emptyFolderDirectory, folder.getName()));
            if (moved) {
                logger.info("Moved empty folder {} to {}", folder.getAbsolutePath(), emptyFolderDirectory.getAbsolutePath());
            } else {
                logger.warn("Failed to move empty folder: {}", folder.getAbsolutePath());
            }
        }
    }

    public void renameDuplicates(File duplicatesSource) throws NoSuchAlgorithmException, IOException {
        File[] files = duplicatesSource.listFiles();
        if (files == null) {
            logger.warn("No files found in duplicates source: {}", duplicatesSource.getAbsolutePath());
            return;
        }
        
        for (File file : files) {
            if (file.isFile()) {
                try {
                    ExifData fileData = new ExifData(file);
                    // Initialize progress trackers from factory to avoid hardcoded paths
                    if (progressTrackerFactory != null) {
                        fileData.setProgressTrackers(
                            progressTrackerFactory.getImageErrorTracker(),
                            progressTrackerFactory.getFileComparisonTracker(),
                            progressTrackerFactory.getFileComparisonTracker()
                        );
                    }
                    String key = mediaFileService.calculateHash(fileData.getFile().toPath());
                    if (fileHash.containsKey(key)) {
                        String originalName = fileHash.get(key).getFile().getName();
                        Path originalPath = file.toPath().getParent().resolve(originalName);
                        Path uniquePath = MediaFileService.findUniqueFileName(originalPath);
                        Files.move(file.toPath(), uniquePath);
                        logger.info("File renamed to: {}", uniquePath);
                    } else {
                        logger.info("No duplicate hash found for file: {}", file.getAbsolutePath());
                    }
                } catch (Exception e) {
                    logger.error("Failed to rename duplicate file: {}", file.getAbsolutePath(), e);
                }
            } else if (file.isDirectory()) {
                if (Objects.requireNonNull(file.listFiles()).length != 0) {
                    renameDuplicates(file);
                }
            }
        }
    }
}