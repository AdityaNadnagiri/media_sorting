package com.media.sort.service;

import com.media.sort.model.OperationRecord;
import com.media.sort.service.TransactionLog.TransactionSession;

import lombok.Getter;
import lombok.Setter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

/**
 * Service to undo file operations using transaction log
 */
@Service
@Getter
@Setter
public class UndoService {

    private static final Logger logger = LoggerFactory.getLogger(UndoService.class);

    @Autowired
    private TransactionLog transactionLog;

    /**
     * Undo all operations from a specific session
     */
    public UndoResult undoSession(String sessionId, String baseDirectory) {
        logger.info("Starting undo for session: {}", sessionId);

        // Load transaction session
        TransactionSession session = transactionLog.loadSession(sessionId, baseDirectory);
        if (session == null) {
            return new UndoResult(false, 0, 0, "Session not found: " + sessionId);
        }

        // Reverse operations (undo in reverse order)
        List<OperationRecord> operations = session.getOperations();
        Collections.reverse(operations);

        int successCount = 0;
        int failCount = 0;
        StringBuilder errors = new StringBuilder();

        for (OperationRecord operation : operations) {
            if (!operation.isCompleted()) {
                logger.debug("Skipping incomplete operation: {}", operation);
                continue;
            }

            try {
                undoOperation(operation);
                successCount++;
                logger.debug("Undone: {} -> {}", operation.getDestinationPath(), operation.getSourcePath());
            } catch (Exception e) {
                failCount++;
                String error = "Failed to undo operation: " + operation + " - " + e.getMessage();
                logger.error(error, e);
                errors.append(error).append("\n");
            }
        }

        logger.info("Undo completed: {} successful, {} failed", successCount, failCount);

        return new UndoResult(failCount == 0, successCount, failCount,
                failCount > 0 ? errors.toString() : null);
    }

    /**
     * Undo a single operation (Java 21 switch expression)
     */
    private void undoOperation(OperationRecord operation) throws IOException {
        switch (operation.getType()) {
            case MOVE -> undoMove(operation);
            case COPY -> undoCopy(operation);
            case DELETE -> logger.warn("Cannot undo DELETE operation: {}", operation);
            case CREATE_FOLDER -> undoCreateFolder(operation);
            default -> logger.warn("Unknown operation type: {}", operation.getType());
        }
    }

    /**
     * Undo a move operation (move file back to original location)
     */
    private void undoMove(OperationRecord operation) throws IOException {
        Path source = operation.getDestinationPath(); // Current location
        Path destination = operation.getSourcePath(); // Original location

        if (!Files.exists(source)) {
            throw new IOException("Cannot undo move - file not found: " + source);
        }

        // Create parent directories if needed
        if (destination.getParent() != null) {
            Files.createDirectories(destination.getParent());
        }

        Files.move(source, destination);
        logger.debug("Moved back: {} -> {}", source, destination);
    }

    /**
     * Undo a copy operation (delete the copy)
     */
    private void undoCopy(OperationRecord operation) throws IOException {
        Path copy = operation.getDestinationPath();

        if (Files.exists(copy)) {
            Files.delete(copy);
            logger.debug("Deleted copy: {}", copy);
        }
    }

    /**
     * Undo folder creation (delete if empty)
     */
    private void undoCreateFolder(OperationRecord operation) throws IOException {
        Path folder = operation.getDestinationPath();

        if (Files.exists(folder) && Files.isDirectory(folder)) {
            // Only delete if empty
            if (Files.list(folder).findAny().isEmpty()) {
                Files.delete(folder);
                logger.debug("Deleted folder: {}", folder);
            } else {
                logger.debug("Folder not empty, skipping: {}", folder);
            }
        }
    }

    /**
     * Result of undo operation (Java Record for immutability)
     */
    public record UndoResult(
            boolean success,
            int successCount,
            int failCount,
            String errorMessage) {

        @Override
        public String toString() {
            return String.format("UndoResult{success=%s, successCount=%d, failCount=%d, error=%s}",
                    success, successCount, failCount, errorMessage);
        }

        public boolean isSuccess() {
            return success;
        }

        public int getSuccessCount() {
            return successCount;
        }

        public int getFailCount() {
            return failCount;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}
