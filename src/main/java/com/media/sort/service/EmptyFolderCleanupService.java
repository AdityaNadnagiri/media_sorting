package com.media.sort.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.DosFileAttributes;
import java.util.*;
import java.util.stream.Stream;

@Service
public class EmptyFolderCleanupService {

    private static final Logger logger = LoggerFactory.getLogger(EmptyFolderCleanupService.class);
    
    private final ProgressTracker cleanupTracker;
    private int foldersDeleted = 0;
    private int foldersSkipped = 0;

    public EmptyFolderCleanupService() {
        this.cleanupTracker = new ProgressTracker("logs/cleanup/empty-folders.txt");
    }

    /**
     * Recursively deletes empty folders in the specified directory.
     * Thoroughly checks for hidden files, system files, and hidden folders before deletion.
     * 
     * @param rootPath The root directory to start cleanup from
     * @return CleanupResult containing statistics about the cleanup operation
     */
    public CleanupResult deleteEmptyFolders(Path rootPath) {
        logger.info("Starting empty folder cleanup for: {}", rootPath);
        foldersDeleted = 0;
        foldersSkipped = 0;
        
        if (!Files.exists(rootPath)) {
            logger.warn("Root path does not exist: {}", rootPath);
            return new CleanupResult(0, 0, "Root path does not exist");
        }

        try {
            // Collect all directories in depth-first order (deepest first)
            List<Path> directories = collectDirectories(rootPath);
            
            // Process directories from deepest to shallowest
            for (Path dir : directories) {
                if (!dir.equals(rootPath)) { // Don't delete the root directory itself
                    processDirectory(dir);
                }
            }
            
            String message = String.format("Cleanup completed. Deleted: %d folders, Skipped: %d folders", 
                                         foldersDeleted, foldersSkipped);
            logger.info(message);
            cleanupTracker.saveProgress("SUMMARY: " + message);
            
            return new CleanupResult(foldersDeleted, foldersSkipped, message);
            
        } catch (IOException e) {
            logger.error("Error during empty folder cleanup", e);
            cleanupTracker.saveProgress("ERROR: " + e.getMessage());
            return new CleanupResult(foldersDeleted, foldersSkipped, "Error: " + e.getMessage());
        }
    }

    /**
     * Collects all directories in the tree, sorted by depth (deepest first)
     */
    private List<Path> collectDirectories(Path rootPath) throws IOException {
        List<Path> directories = new ArrayList<>();
        
        Files.walkFileTree(rootPath, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                directories.add(dir);
                return FileVisitResult.CONTINUE;
            }
        });
        
        // Sort by depth (deepest first) to ensure we delete child directories before parent directories
        directories.sort((a, b) -> Integer.compare(b.getNameCount(), a.getNameCount()));
        
        return directories;
    }

    /**
     * Processes a single directory for deletion if it's truly empty
     */
    private void processDirectory(Path directory) {
        try {
            if (isTrulyEmpty(directory)) {
                Files.delete(directory);
                foldersDeleted++;
                logger.info("Deleted empty folder: {}", directory);
                cleanupTracker.saveProgress("DELETED: " + directory);
            } else {
                foldersSkipped++;
                logger.debug("Skipped non-empty folder: {}", directory);
                cleanupTracker.saveProgress("SKIPPED: " + directory + " (not empty)");
            }
        } catch (DirectoryNotEmptyException e) {
            foldersSkipped++;
            logger.debug("Folder not empty: {}", directory);
            cleanupTracker.saveProgress("SKIPPED: " + directory + " (DirectoryNotEmptyException)");
        } catch (IOException e) {
            foldersSkipped++;
            logger.warn("Could not delete folder {}: {}", directory, e.getMessage());
            cleanupTracker.saveProgress("ERROR: " + directory + " - " + e.getMessage());
        }
    }

    /**
     * Thoroughly checks if a directory is truly empty, including hidden files and system files
     */
    private boolean isTrulyEmpty(Path directory) {
        try (Stream<Path> entries = Files.list(directory)) {
            List<Path> allEntries = entries.collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
            
            if (allEntries.isEmpty()) {
                return true; // Directory is empty
            }
            
            // Check each entry to see if any should prevent deletion
            for (Path entry : allEntries) {
                if (shouldPreventDeletion(entry)) {
                    logger.debug("Found file/folder that prevents deletion: {} (hidden: {}, system: {})", 
                               entry.getFileName(), 
                               isHidden(entry), 
                               isSystemFile(entry));
                    return false;
                }
            }
            
            return true; // All entries are safe to ignore
            
        } catch (IOException e) {
            logger.warn("Error checking if directory is empty: {}", directory, e);
            return false; // Err on the side of caution
        }
    }

    /**
     * Determines if a file or folder should prevent the parent directory from being deleted
     */
    private boolean shouldPreventDeletion(Path path) {
        try {
            String fileName = path.getFileName().toString().toLowerCase();
            
            // Always preserve certain system files/folders
            if (isSystemCriticalFile(fileName) || isImportantHiddenFolder(fileName)) {
                return true;
            }
            
            // Check if it's a regular file (including hidden files)
            if (Files.isRegularFile(path)) {
                // Most regular files should prevent deletion
                // Exception: some temporary or cache files might be ignored
                return !isIgnorableFile(fileName);
            }
            
            // Check if it's a directory
            if (Files.isDirectory(path)) {
                // Hidden directories that are important should prevent deletion
                if (isImportantHiddenFolder(fileName)) {
                    return true;
                }
                
                // For other directories, check if they're truly empty recursively
                return !isTrulyEmpty(path);
            }
            
            // For any other type of file system entry, preserve it
            return true;
            
        } catch (Exception e) {
            logger.debug("Error checking path {}: {}", path, e.getMessage());
            return true; // Err on the side of caution
        }
    }

    /**
     * Checks if a file is hidden (cross-platform)
     */
    private boolean isHidden(Path path) {
        try {
            // Check using Files.isHidden (works on most platforms)
            if (Files.isHidden(path)) {
                return true;
            }
            
            // Additional check for Windows hidden attribute
            if (Files.getFileStore(path).supportsFileAttributeView("dos")) {
                DosFileAttributes dosAttrs = Files.readAttributes(path, DosFileAttributes.class);
                return dosAttrs.isHidden();
            }
            
            // Unix-style hidden files (starting with .)
            String fileName = path.getFileName().toString();
            return fileName.startsWith(".");
            
        } catch (IOException e) {
            logger.debug("Could not determine if file is hidden: {}", path);
            return false;
        }
    }

    /**
     * Checks if a file has system attributes
     */
    private boolean isSystemFile(Path path) {
        try {
            if (Files.getFileStore(path).supportsFileAttributeView("dos")) {
                DosFileAttributes dosAttrs = Files.readAttributes(path, DosFileAttributes.class);
                return dosAttrs.isSystem();
            }
        } catch (IOException e) {
            logger.debug("Could not determine if file is system file: {}", path);
        }
        return false;
    }

    /**
     * Identifies system-critical files that should never allow parent folder deletion
     */
    private boolean isSystemCriticalFile(String fileName) {
        Set<String> criticalFiles = Set.of(
            "desktop.ini",
            "thumbs.db",
            ".ds_store",
            "folder.jpg",
            "albumartsmall.jpg",
            "albumart.jpg",
            ".nomedia"
        );
        return criticalFiles.contains(fileName);
    }

    /**
     * Identifies important hidden folders that should prevent parent deletion
     */
    private boolean isImportantHiddenFolder(String folderName) {
        Set<String> importantFolders = Set.of(
            ".git",
            ".svn",
            ".hg",
            ".bzr",
            ".idea",
            ".vscode",
            ".settings",
            ".metadata",
            "node_modules",
            ".gradle",
            ".maven",
            "__pycache__"
        );
        return importantFolders.contains(folderName);
    }

    /**
     * Identifies files that can be safely ignored (temporary files, etc.)
     */
    private boolean isIgnorableFile(String fileName) {
        // Temporary files that can be safely ignored
        return fileName.endsWith(".tmp") || 
               fileName.endsWith(".temp") || 
               fileName.startsWith("~") ||
               fileName.equals(".empty");
    }

    /**
     * Deletes empty folders in a specific directory with safety checks
     * 
     * @param folderPath Path to the directory to clean up
     * @return CleanupResult with operation statistics
     */
    public CleanupResult deleteEmptyFolders(String folderPath) {
        return deleteEmptyFolders(Paths.get(folderPath));
    }

    /**
     * Result class for cleanup operations
     */
    public static class CleanupResult {
        private final int foldersDeleted;
        private final int foldersSkipped;
        private final String message;

        public CleanupResult(int foldersDeleted, int foldersSkipped, String message) {
            this.foldersDeleted = foldersDeleted;
            this.foldersSkipped = foldersSkipped;
            this.message = message;
        }

        public int getFoldersDeleted() { return foldersDeleted; }
        public int getFoldersSkipped() { return foldersSkipped; }
        public String getMessage() { return message; }

        @Override
        public String toString() {
            return String.format("CleanupResult{deleted=%d, skipped=%d, message='%s'}", 
                               foldersDeleted, foldersSkipped, message);
        }
    }
}