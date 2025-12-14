package com.media.sort.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

@Service
public class FileExtensionAnalysisService {

    private static final Logger logger = LoggerFactory.getLogger(FileExtensionAnalysisService.class);

    private static final Set<String> IMAGE_EXTENSIONS = new HashSet<>(Arrays.asList(
            "arw", "jpg", "jpeg", "png", "bmp", "ico", "tif", "tiff", "raw", "indd",
            "ai", "eps", "pdf", "heic", "cr2", "nrw", "k25", "gif"));

    private static final Set<String> VIDEO_EXTENSIONS = new HashSet<>(Arrays.asList(
            "mp4", "mkv", "flv", "avi", "mov", "wmv", "rm", "mpg", "mpeg",
            "3gp", "vob", "m4v", "3g2", "divx", "xvid"));

    public void analyzeExtensions(String folderPath, String outputFolder) {
        logger.info("Starting file extension analysis for folder: {}", folderPath);

        Set<String> fileExtensions = new HashSet<>();

        try {
            // Create output directory if it doesn't exist
            Files.createDirectories(Paths.get(outputFolder));

            // Walk through all files and collect extensions
            Files.walk(Paths.get(folderPath))
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString().toLowerCase())
                    .filter(fileName -> fileName.contains("."))
                    .map(fileName -> fileName.substring(fileName.lastIndexOf('.') + 1))
                    .forEach(fileExtensions::add);

            // Write different extension types to separate files
            writeToFile(outputFolder + "/ImageExtensions.log", fileExtensions, IMAGE_EXTENSIONS::contains);
            writeToFile(outputFolder + "/VideoExtensions.log", fileExtensions, VIDEO_EXTENSIONS::contains);
            writeToFile(outputFolder + "/OtherExtensions.log", fileExtensions,
                    extension -> !IMAGE_EXTENSIONS.contains(extension) && !VIDEO_EXTENSIONS.contains(extension));

            logger.info("File extension analysis completed. Results saved to: {}", outputFolder);

        } catch (IOException e) {
            logger.error("Error during file extension analysis", e);
            throw new RuntimeException("Failed to analyze file extensions", e);
        }
    }

    private void writeToFile(String filePath, Set<String> fileExtensions, Predicate<String> filter) throws IOException {
        try (PrintWriter writer = new PrintWriter(filePath, StandardCharsets.UTF_8)) {
            fileExtensions.stream()
                    .filter(filter)
                    .sorted()
                    .forEach(writer::println);
        }
        logger.debug("Written extensions to file: {}", filePath);
    }

    public Set<String> getImageExtensions() {
        return new HashSet<>(IMAGE_EXTENSIONS);
    }

    public Set<String> getVideoExtensions() {
        return new HashSet<>(VIDEO_EXTENSIONS);
    }

    public boolean isImageExtension(String extension) {
        return IMAGE_EXTENSIONS.contains(extension.toLowerCase());
    }

    public boolean isVideoExtension(String extension) {
        return VIDEO_EXTENSIONS.contains(extension.toLowerCase());
    }
}