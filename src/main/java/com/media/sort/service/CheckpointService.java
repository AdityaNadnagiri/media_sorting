package com.media.sort.service;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Getter;
import lombok.Setter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Service for incremental processing with checkpoint/resume capability
 */
@Service
public class CheckpointService {

    private static final Logger logger = LoggerFactory.getLogger(CheckpointService.class);
    private static final String CHECKPOINT_FILE = "checkpoint.json";

    private final ObjectMapper objectMapper;

    public CheckpointService() {
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Save processing checkpoint
     */
    public void saveCheckpoint(String baseDirectory, Checkpoint checkpoint) {
        Path checkpointPath = Paths.get(baseDirectory, CHECKPOINT_FILE);

        try {
            checkpoint.setLastUpdated(new Date());
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(checkpointPath.toFile(), checkpoint);
            logger.info("Checkpoint saved: {} / {} files processed",
                    checkpoint.getProcessedCount(), checkpoint.getTotalCount());
        } catch (IOException e) {
            logger.error("Failed to save checkpoint", e);
        }
    }

    /**
     * Load existing checkpoint
     */
    public Checkpoint loadCheckpoint(String baseDirectory) {
        Path checkpointPath = Paths.get(baseDirectory, CHECKPOINT_FILE);

        if (!Files.exists(checkpointPath)) {
            return null;
        }

        try {
            Checkpoint checkpoint = objectMapper.readValue(checkpointPath.toFile(), Checkpoint.class);
            logger.info("Checkpoint loaded: {} files already processed",
                    checkpoint.getProcessedCount());
            return checkpoint;
        } catch (IOException e) {
            logger.error("Failed to load checkpoint", e);
            return null;
        }
    }

    /**
     * Delete checkpoint file
     */
    public void deleteCheckpoint(String baseDirectory) {
        Path checkpointPath = Paths.get(baseDirectory, CHECKPOINT_FILE);

        try {
            if (Files.exists(checkpointPath)) {
                Files.delete(checkpointPath);
                logger.info("Checkpoint deleted");
            }
        } catch (IOException e) {
            logger.error("Failed to delete checkpoint", e);
        }
    }

    /**
     * Check if checkpoint exists
     */
    public boolean hasCheckpoint(String baseDirectory) {
        Path checkpointPath = Paths.get(baseDirectory, CHECKPOINT_FILE);
        return Files.exists(checkpointPath);
    }

    /**
     * Checkpoint data model
     */
    @Setter
    @Getter
    public static class Checkpoint {
        private int processedCount;
        private int totalCount;
        private List<String> processedFiles;
        private Date lastUpdated;
        private Date startTime;

        public Checkpoint() {
            this.processedFiles = new ArrayList<>();
            this.startTime = new Date();
        }

        public void addProcessedFile(String filePath) {
            processedFiles.add(filePath);
            processedCount++;
        }

        public boolean isFileProcessed(String filePath) {
            return processedFiles.contains(filePath);
        }

        @JsonIgnore
        public int getProgress() {
            if (totalCount == 0)
                return 0;
            return (processedCount * 100) / totalCount;
        }
    }
}
