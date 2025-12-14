package com.media.sort.service;

import com.media.sort.model.ExifData;
import com.media.sort.util.FileOperationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Service
public class MediaFileService {

    private static final Logger logger = LoggerFactory.getLogger(MediaFileService.class);

    private ProgressTracker mediaErrorTracker;

    @Autowired
    private ProgressTrackerFactory progressTrackerFactory;

    public MediaFileService() {
        // mediaErrorTracker will be initialized through initializeTracker method
    }

    private void initializeTracker() {
        if (progressTrackerFactory != null && mediaErrorTracker == null) {
            this.mediaErrorTracker = progressTrackerFactory.getMediaErrorTracker();
        }
    }

    /**
     * Processes a file, calculating its hash and moving it to a new location.
     */
    public void processFile(ExifData fileData, PhotoOrganizerService photoOrganizerService) {
        initializeTracker(); // Ensure tracker is initialized
        try {
            String key = calculateHash(fileData.getFile().toPath());
            photoOrganizerService.moveImageOrVideoFile(fileData, key);
        } catch (IOException | NoSuchAlgorithmException e) {
            logger.error("Failed to process file: {}", fileData.getFile().getAbsolutePath(), e);
            mediaErrorTracker.saveProgress("ProcessFile file: " + fileData.getFile().getPath());
        }
    }

    /**
     * Calculates the SHA-256 hash of a file.
     *
     * @param filePath The path of the file to hash.
     * @return The SHA-256 hash of the file.
     * @throws IOException              If an I/O error occurs.
     * @throws NoSuchAlgorithmException If the SHA-256 algorithm is not available.
     */
    public String calculateHash(Path filePath) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            logger.error("SHA-256 algorithm not available for file: {}", filePath, e);
            mediaErrorTracker.saveProgress("CalculateHash processing file: " + filePath);
            throw new NoSuchAlgorithmException("SHA-256 algorithm not available", e);
        }

        try (InputStream inputStream = new FileInputStream(filePath.toFile())) {
            byte[] buffer = new byte[8192]; // 8KB buffer
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
        } catch (IOException e) {
            logger.error("Error reading file while calculating hash: {}", filePath, e);
            mediaErrorTracker.saveProgress("CalculateHash file:: " + filePath);
            throw new IOException("Error reading file while calculating hash", e);
        }

        byte[] hashBytes = digest.digest();
        StringBuilder hashBuilder = new StringBuilder();
        for (byte hashByte : hashBytes) {
            hashBuilder.append(String.format("%02x", hashByte));
        }
        return hashBuilder.toString();
    }

    /**
     * Moves a file to the destination folder with smart renaming.
     * 
     * @param fileData          The data of the file to move.
     * @param destinationFolder The folder to move the file to.
     */
    public void executeMove(ExifData fileData, File destinationFolder) {
        executeMove(fileData, destinationFolder, false);
    }

    /**
     * Moves a file to the destination folder with smart renaming based on whether
     * it's a duplicate.
     * - Originals: Get clean names (remove any _1, _2 suffixes)
     * - Duplicates: Get numbered suffixes (_1, _2, etc.)
     * 
     * @param fileData          The data of the file to move.
     * @param destinationFolder The folder to move the file to.
     * @param isDuplicate       Whether this file is a duplicate (true) or original
     *                          (false).
     */
    public void executeMove(ExifData fileData, File destinationFolder, boolean isDuplicate) {
        Path destinationPath;
        String deviceModel = fileData.getDeviceModel();

        if (deviceModel != null) {
            destinationFolder = new File(destinationFolder.getPath(), deviceModel);
        } else {
            destinationFolder = new File(destinationFolder.getPath(), fileData.getExtension());
        }

        File currentFile = fileData.getFile();
        try {
            if (createDirectory(destinationFolder)) {
                String fileName = currentFile.getName();

                if (isDuplicate) {
                    // For duplicates: Add numbered suffix if needed
                    destinationPath = destinationFolder.toPath().resolve(fileName);
                    destinationPath = FileOperationUtils.findUniqueFileName(destinationPath);
                } else {
                    // For originals: Remove any existing suffix and use clean name
                    String cleanFileName = removeNumberedSuffix(fileName);
                    destinationPath = destinationFolder.toPath().resolve(cleanFileName);

                    // If clean name already exists, we need to resolve the conflict
                    if (Files.exists(destinationPath)) {
                        logger.info("Conflict detected: {} already exists in Original folder", cleanFileName);

                        // Load the existing file's metadata to compare dates
                        File existingFile = destinationPath.toFile();
                        ExifData existingFileData = new ExifData(existingFile);

                        // Compare dates to determine which is the true original
                        boolean currentIsNewer = fileData.isAfter(existingFileData);

                        if (currentIsNewer) {
                            // Current file is NEWER - existing file is the true original
                            // Current file should go to duplicates
                            logger.info("Current file {} is newer, keeping existing {} as Original (older)",
                                    currentFile.getName(), existingFile.getName());

                            // Move current file to Duplicates
                            File duplicateFolder = determineDuplicateFolder(fileData, destinationFolder);
                            createDirectory(duplicateFolder); // Ensure directory exists
                            destinationPath = duplicateFolder.toPath().resolve(cleanFileName);
                            destinationPath = FileOperationUtils.findUniqueFileName(destinationPath);
                        } else {
                            // Current file is OLDER - it's the true original
                            // Move existing file to Duplicates first
                            logger.info("Current file {} is older (true original), moving existing {} to Duplicates",
                                    currentFile.getName(), existingFile.getName());

                            // Determine duplicate folder path
                            File duplicateFolder = determineDuplicateFolder(fileData, destinationFolder);
                            createDirectory(duplicateFolder); // Ensure directory exists
                            Path duplicatePath = duplicateFolder.toPath().resolve(cleanFileName);
                            duplicatePath = FileOperationUtils.findUniqueFileName(duplicatePath);

                            // Move existing file to duplicates
                            Files.move(existingFile.toPath(), duplicatePath);
                            logger.info("Moved existing file to Duplicates: {}", duplicatePath);

                            // Now current file can take the clean name in Original
                            // destinationPath is already set to the clean name
                        }
                    }
                }

                Path path = Files.move(currentFile.toPath(), destinationPath, StandardCopyOption.REPLACE_EXISTING);
                fileData.setFile(path.toFile());
                fileData.logFileDetails("Moved to " + destinationPath);
                logger.info("Successfully moved file {} to {}", currentFile.getAbsolutePath(), destinationPath);
            }
        } catch (IOException e) {
            logger.error("Failed to execute move for file: {}", currentFile.getAbsolutePath(), e);
            mediaErrorTracker.saveProgress("ExecuteMove processing File:: " + currentFile.toPath());
        }
    }

    /**
     * Determines the appropriate duplicate folder based on file type and current
     * destination.
     */
    private File determineDuplicateFolder(ExifData fileData, File originalFolder) {
        // Extract the base path and replace "Original" with "Duplicate"
        String originalPath = originalFolder.getAbsolutePath();
        String duplicatePath = originalPath.replace("\\Original\\", "\\Duplicate\\")
                .replace("/Original/", "/Duplicate/");
        return new File(duplicatePath);
    }

    /**
     * Removes numbered suffixes like _1, _2, _copy, etc. from filename.
     * Examples:
     * IMG_001_1.jpg -> IMG_001.jpg
     * photo_2.png -> photo.png
     * video_copy.mp4 -> video.mp4
     */
    private String removeNumberedSuffix(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot == -1) {
            return fileName; // No extension
        }

        String nameWithoutExt = fileName.substring(0, lastDot);
        String extension = fileName.substring(lastDot);

        // Remove common suffixes: _1, _2, _copy, _duplicate, etc.
        nameWithoutExt = nameWithoutExt.replaceAll("_\\d+$", ""); // Remove _1, _2, etc.
        nameWithoutExt = nameWithoutExt.replaceAll("_copy$", ""); // Remove _copy
        nameWithoutExt = nameWithoutExt.replaceAll("_duplicate$", ""); // Remove _duplicate
        nameWithoutExt = nameWithoutExt.replaceAll("\\s*\\(\\d+\\)$", ""); // Remove (1), (2), etc.

        return nameWithoutExt + extension;
    }

    /**
     * @deprecated Use FileOperationUtils.findUniqueFileName instead
     */
    @Deprecated
    public static synchronized Path findUniqueFileName(Path path) {
        return FileOperationUtils.findUniqueFileName(path);
    }

    /**
     * Creates a directory if it does not already exist.
     *
     * @param directory The directory to create.
     * @return true if the directory was created or already exists, false otherwise.
     */
    public static boolean createDirectory(File directory) {
        if (!directory.exists()) {
            boolean created = directory.mkdirs();
            if (created) {
                Logger logger = LoggerFactory.getLogger(MediaFileService.class);
                logger.info("Created directory: {}", directory.getAbsolutePath());
            }
            return created;
        }
        return true;
    }
}