package com.media.sort.batch.reader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemReader;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Custom ItemReader for reading files from a folder.
 * Used in folder comparison batch job.
 */
public class FolderFileReader implements ItemReader<File> {

    private static final Logger logger = LoggerFactory.getLogger(FolderFileReader.class);

    private final String folderPath;
    private List<File> files;
    private int currentIndex = 0;

    public FolderFileReader(String folderPath) {
        this.folderPath = folderPath;
        this.files = new ArrayList<>();
        scanFolder();
    }

    /**
     * Scan folder for all files
     */
    private void scanFolder() {
        logger.info("Scanning folder: {}", folderPath);
        Path directory = Path.of(folderPath);

        if (!Files.exists(directory) || !Files.isDirectory(directory)) {
            logger.error("Folder does not exist or is not a directory: {}", folderPath);
            return;
        }

        try (Stream<Path> paths = Files.walk(directory)) {
            paths.filter(Files::isRegularFile)
                    .forEach(path -> files.add(path.toFile()));
            logger.info("Found {} files in folder: {}", files.size(), folderPath);
        } catch (Exception e) {
            logger.error("Error scanning folder: {}", folderPath, e);
        }
    }

    @Override
    public File read() {
        if (currentIndex < files.size()) {
            File file = files.get(currentIndex);
            currentIndex++;
            return file;
        }
        return null; // End of data
    }
}
