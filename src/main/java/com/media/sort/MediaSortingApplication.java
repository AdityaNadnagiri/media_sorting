package com.media.sort;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(MediaSortingProperties.class)
public class MediaSortingApplication {

    public static void main(String[] args) {
        // Set LOG_DIR before any Spring/logback initialization
        initializeLogDirectory();

        SpringApplication.run(MediaSortingApplication.class, args);
    }

    /**
     * Initialize the log directory BEFORE Spring Boot starts.
     * This ensures Logback can use the LOG_DIR system property.
     */
    private static void initializeLogDirectory() {
        String timestamp = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        String baseLogsDir = System.getProperty("ROOT_LOGS_FOLDER", "logs");
        String runLogDirectory = baseLogsDir + "/run_" + timestamp;

        // Set system property for Logback to use
        System.setProperty("LOG_DIR", runLogDirectory);

        // Create the directory
        try {
            java.nio.file.Files.createDirectories(java.nio.file.Paths.get(runLogDirectory));
        } catch (Exception e) {
            // Fallback to base logs directory
            System.setProperty("LOG_DIR", baseLogsDir);
            System.err.println("Failed to create log directory: " + e.getMessage());
        }
    }
}