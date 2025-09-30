package com.media.sort.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Utility class for common directory and file operations
 */
public final class FileOperationUtils {
    
    private FileOperationUtils() {
        // Utility class - prevent instantiation
    }
    
    /**
     * Creates a directory if it doesn't exist
     * @param directory The directory to create
     * @return true if directory exists or was created, false otherwise
     */
    public static boolean createDirectoryIfNotExists(File directory) {
        if (directory == null) {
            return false;
        }
        
        if (directory.exists()) {
            return directory.isDirectory();
        }
        
        return directory.mkdirs();
    }
    
    /**
     * Creates a directory if it doesn't exist (Path version)
     */
    public static boolean createDirectoryIfNotExists(Path directory) {
        if (directory == null) {
            return false;
        }
        
        try {
            Files.createDirectories(directory);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
    
    /**
     * Finds a unique filename by appending a counter if file already exists
     */
    public static synchronized Path findUniqueFileName(Path path) {
        if (!Files.exists(path)) {
            return path;
        }
        
        String fileName = path.getFileName().toString();
        String baseName = fileName.substring(0, fileName.lastIndexOf('.'));
        String extension = fileName.substring(fileName.lastIndexOf('.'));

        // Remove parentheses and spaces, and copy indicators
        baseName = baseName.replaceAll("\\s*\\(\\d+\\)\\s*", "")
                          .replaceAll("(?i) - copy", "")
                          .replaceAll("(?i)copy", "");
        
        Path uniquePath = path.resolveSibling(baseName + extension);
        int counter = 1;

        while (Files.exists(uniquePath)) {
            uniquePath = path.resolveSibling(baseName + "(" + counter + ")" + extension);
            counter++;
        }
        return uniquePath;
    }
    
    /**
     * Safely moves a file to destination, creating directories as needed
     */
    public static boolean safeMove(Path source, Path destination) {
        try {
            // Create parent directories if they don't exist
            Path parentDir = destination.getParent();
            if (parentDir != null) {
                Files.createDirectories(parentDir);
            }
            
            // Find unique destination if file already exists
            Path uniqueDestination = findUniqueFileName(destination);
            
            // Move the file
            Files.move(source, uniqueDestination, StandardCopyOption.REPLACE_EXISTING);
            return true;
            
        } catch (IOException e) {
            return false;
        }
    }
    
    /**
     * Checks if a directory is empty (no files or subdirectories)
     */
    public static boolean isDirectoryEmpty(Path directory) {
        try {
            return Files.list(directory)
                       .findAny()
                       .isEmpty();
        } catch (IOException e) {
            return false;
        }
    }
}