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
     * Moves a file to a new location.
     *
     * @param fileData          The data of the file to move.
     * @param destinationFolder The folder to move the file to.
     */
    public void executeMove(ExifData fileData, File destinationFolder) {
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
                destinationPath = destinationFolder.toPath().resolve(currentFile.getName());
                destinationPath = FileOperationUtils.findUniqueFileName(destinationPath);
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