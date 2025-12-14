package com.media.sort.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.*;
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

@Component
public class ProgressTracker {

    private static final Logger logger = LoggerFactory.getLogger(ProgressTracker.class);

    private final String directory;
    private final String baseFileName;
    private int linesCount = 0;
    private Path currentFile;

    public ProgressTracker() {
        this("logs");
    }

    public ProgressTracker(String filePath) {
        if (filePath.contains("/") || filePath.contains("\\")) {
            // It's a full file path
            Path path = Paths.get(filePath);
            this.directory = path.getParent() != null ? path.getParent().toString() : ".";
            this.baseFileName = path.getFileName().toString();
        } else {
            // It's just a directory name
            this.directory = filePath;
            this.baseFileName = "progress.txt"; // Default filename for directory-only paths
        }
        createDirectoryIfNotExists();
        this.currentFile = getLatestFile();
    }

    private void createDirectoryIfNotExists() {
        try {
            Files.createDirectories(Paths.get(directory));
        } catch (IOException e) {
            logger.error("Failed to create directory: {}", directory, e);
            throw new UncheckedIOException(e);
        }
    }

    private Path getLatestFile() {
        try (Stream<Path> paths = Files.list(Paths.get(directory))) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith(baseFileName))
                    .max(Comparator.comparingInt(this::getVersionNumber))
                    .orElseGet(this::createNewFile);
        } catch (IOException e) {
            logger.error("Failed to get latest file in directory: {}", directory, e);
            throw new UncheckedIOException(e);
        }
    }

    private int getVersionNumber(Path path) {
        if (path != null) {
            String name = path.getFileName().toString();
            int index = name.lastIndexOf('_');
            if (index != -1) {
                String version = name.substring(index + 1);
                try {
                    return Integer.parseInt(version);
                } catch (NumberFormatException ignored) {
                    // Ignore and return 0
                }
            }
        }
        return 0;
    }

    private Path createNewFile() {
        try {
            Path newFile = Paths.get(directory, baseFileName + "_" + (getVersionNumber(currentFile) + 1));
            Files.createFile(newFile);
            logger.info("New file created: {}", newFile);
            return newFile;
        } catch (IOException e) {
            logger.error("Failed to create new file", e);
            throw new UncheckedIOException(e);
        }
    }

    public void saveProgress(String processedFilePath) {
        try (BufferedWriter writer = Files.newBufferedWriter(currentFile, StandardOpenOption.APPEND)) {
            writer.write(processedFilePath);
            writer.newLine();
            linesCount++;
            if (linesCount >= 2500) {
                linesCount = 0;
                currentFile = createNewFile();
            }
        } catch (IOException e) {
            logger.error("Failed to save progress for file: {}", processedFilePath, e);
            throw new UncheckedIOException(e);
        }
    }

    public String loadProgress() {
        try {
            return new String(Files.readAllBytes(currentFile));
        } catch (IOException e) {
            logger.error("Failed to load progress", e);
            throw new UncheckedIOException(e);
        }
    }

    public void saveFolder2Files(ConcurrentHashMap<String, String> folder2Files) {
        try (ObjectOutputStream oos = new ObjectOutputStream(
                new FileOutputStream(Paths.get(directory, "folder2Files.ser").toString()))) {
            oos.writeObject(folder2Files);
        } catch (IOException e) {
            logger.error("Failed to save folder2Files mapping", e);
        }
    }

    @SuppressWarnings("unchecked")
    public ConcurrentHashMap<String, String> loadFolder2Files() {
        try (ObjectInputStream ois = new ObjectInputStream(
                new FileInputStream(Paths.get(directory, "folder2Files.ser").toString()))) {
            return (ConcurrentHashMap<String, String>) ois.readObject();
        } catch (Exception e) {
            logger.debug("No existing folder2Files mapping found, returning empty map");
            return new ConcurrentHashMap<>();
        }
    }

    public String validateAndLoadProgress(ConcurrentHashMap<String, String> folder2Files) {
        try (RandomAccessFile raf = new RandomAccessFile(currentFile.toString(), "rw")) {
            long pointer = raf.length() - 1;
            StringBuilder sb = new StringBuilder();

            while (pointer >= 0) {
                raf.seek(pointer);
                char c = (char) raf.read();
                if (c == '\n') {
                    String filePath = sb.reverse().toString();
                    if (folder2Files.containsKey(filePath)) {
                        return filePath;
                    } else {
                        raf.setLength(pointer + 1); // delete the line
                    }
                    sb = new StringBuilder();
                } else {
                    sb.append(c);
                }
                pointer--;
            }
        } catch (IOException e) {
            logger.error("Failed to validate and load progress", e);
            throw new UncheckedIOException(e);
        }
        return null;
    }
}