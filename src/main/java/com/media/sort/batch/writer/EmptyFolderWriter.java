package com.media.sort.batch.writer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ItemWriter for moving empty folders to EmptyFolder directory.
 * Tracks the count of moved folders.
 */
public class EmptyFolderWriter implements ItemWriter<File> {

    private static final Logger logger = LoggerFactory.getLogger(EmptyFolderWriter.class);

    private final AtomicInteger movedCount = new AtomicInteger(0);
    private final String sourceFolder;
    private Path emptyFolderDir;

    public EmptyFolderWriter(String sourceFolder) {
        this.sourceFolder = sourceFolder;
    }

    @Override
    public void write(Chunk<? extends File> chunk) throws Exception {
        // Initialize EmptyFolder directory on first write
        if (emptyFolderDir == null) {
            emptyFolderDir = new File(sourceFolder, "EmptyFolder").toPath();
            Files.createDirectories(emptyFolderDir);
            logger.info("Created EmptyFolder directory: {}", emptyFolderDir);
        }

        for (File folder : chunk) {
            moveEmptyFolder(folder);
        }
    }

    private void moveEmptyFolder(File folder) {
        if (folder.exists() && folder.isDirectory()) {
            // Don't move the EmptyFolder directory itself
            if (folder.toPath().equals(emptyFolderDir) || folder.toPath().startsWith(emptyFolderDir)) {
                logger.debug("Skipping EmptyFolder directory itself: {}", folder.getAbsolutePath());
                return;
            }

            File[] files = folder.listFiles();
            if (files != null && files.length == 0) {
                try {
                    // Create unique name for moved folder
                    String folderName = folder.getName();
                    Path targetPath = emptyFolderDir.resolve(folderName);

                    // If target already exists, append number
                    int counter = 1;
                    while (Files.exists(targetPath)) {
                        targetPath = emptyFolderDir.resolve(folderName + "_" + counter);
                        counter++;
                    }

                    // Move the empty folder
                    Files.move(folder.toPath(), targetPath);
                    movedCount.incrementAndGet();
                    logger.info("Moved empty folder: {} -> {}", folder.getAbsolutePath(), targetPath);
                } catch (Exception e) {
                    logger.warn("Failed to move empty folder {}: {}", folder.getAbsolutePath(), e.getMessage());
                }
            } else {
                logger.debug("Folder is not empty, skipping: {}", folder.getAbsolutePath());
            }
        }
    }

    public int getMovedCount() {
        return movedCount.get();
    }
}
