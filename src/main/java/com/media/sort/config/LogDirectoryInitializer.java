package com.media.sort.config;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.lang.NonNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Initializer that sets up the log directory BEFORE Spring Boot starts
 * This ensures Logback can use the LOG_DIR system property
 */
public class LogDirectoryInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(@NonNull ConfigurableApplicationContext applicationContext) {
        // Create unique directory name based on timestamp
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        String baseLogsDir = System.getProperty("ROOT_LOGS_FOLDER", "logs");
        String runLogDirectory = baseLogsDir + "/run_" + timestamp;

        // Set system property BEFORE Logback initializes
        System.setProperty("LOG_DIR", runLogDirectory);

        // Create the directory
        try {
            Path runLogPath = Paths.get(runLogDirectory);
            Files.createDirectories(runLogPath);
            // Don't print here - logging isn't initialized yet
        } catch (Exception e) {
            // Fallback to base logs directory
            System.setProperty("LOG_DIR", baseLogsDir);
        }
    }
}
