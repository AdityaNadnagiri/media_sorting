package com.media.sort.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.media.sort.model.OperationRecord;

import lombok.Getter;
import lombok.Setter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Service to log all file operations for undo capability
 */
@Service
public class TransactionLog {

    private static final Logger logger = LoggerFactory.getLogger(TransactionLog.class);

    @Value("${media.transaction-log.directory:transactions}")
    private String transactionDirName;

    @Value("${media.transaction-log.auto-save-count:10}")
    private int autoSaveCount;

    private final ObjectMapper objectMapper;
    private String currentSessionId;
    private List<OperationRecord> currentSession;
    private Path transactionLogPath;

    public TransactionLog() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.objectMapper.setDateFormat(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));
        this.currentSession = new ArrayList<>();

        // Set defaults for testing (will be overridden by Spring @Value in production)
        if (this.transactionDirName == null) {
            this.transactionDirName = "transactions";
        }
        if (this.autoSaveCount == 0) {
            this.autoSaveCount = 10;
        }
    }

    /**
     * Start a new transaction session
     */
    public void startSession(String baseDirectory) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HHmmss");
        this.currentSessionId = sdf.format(new Date());
        this.currentSession = new ArrayList<>();

        // Create transactions directory
        Path transactionDirPath = Paths.get(baseDirectory, transactionDirName);
        try {
            Files.createDirectories(transactionDirPath);
            this.transactionLogPath = transactionDirPath.resolve(currentSessionId + ".json");
            logger.info("Transaction log started: {}", transactionLogPath);
        } catch (IOException e) {
            logger.error("Failed to create transaction directory", e);
        }
    }

    /**
     * Check if there is an active session
     */
    public boolean hasActiveSession() {
        return currentSessionId != null && currentSession != null;
    }

    /**
     * Log a file operation
     */
    public void logOperation(OperationRecord operation) {
        currentSession.add(operation);

        // Save to disk every N operations for safety (configurable)
        if (currentSession.size() % autoSaveCount == 0) {
            saveSession();
        }
    }

    /**
     * Save current session to disk
     */
    public void saveSession() {
        if (transactionLogPath == null || currentSession.isEmpty()) {
            return;
        }

        try {
            TransactionSession session = new TransactionSession();
            session.setSessionId(currentSessionId);
            session.setTimestamp(new Date());
            session.setOperations(currentSession);
            session.setTotalOperations(currentSession.size());

            objectMapper.writeValue(transactionLogPath.toFile(), session);
            logger.debug("Transaction log saved: {} operations", currentSession.size());
        } catch (IOException e) {
            logger.error("Failed to save transaction log", e);
        }
    }

    /**
     * End transaction session and save final state
     */
    public void endSession() {
        saveSession();
        logger.info("Transaction session ended: {} operations logged", currentSession.size());
        currentSession = new ArrayList<>();
    }

    /**
     * Load a transaction session from file
     */
    public TransactionSession loadSession(String sessionId, String baseDirectory) {
        Path logPath = Paths.get(baseDirectory, transactionDirName, sessionId + ".json");

        if (!Files.exists(logPath)) {
            logger.error("Transaction log not found: {}", logPath);
            return null;
        }

        try {
            return objectMapper.readValue(logPath.toFile(), TransactionSession.class);
        } catch (IOException e) {
            logger.error("Failed to load transaction log", e);
            return null;
        }
    }

    /**
     * List all available transaction sessions
     */
    public List<String> listSessions(String baseDirectory) {
        Path transactionDirPath = Paths.get(baseDirectory, transactionDirName);
        List<String> sessions = new ArrayList<>();

        if (!Files.exists(transactionDirPath)) {
            return sessions;
        }

        try {
            Files.list(transactionDirPath)
                    .filter(path -> path.toString().endsWith(".json"))
                    .forEach(path -> {
                        String filename = path.getFileName().toString();
                        sessions.add(filename.replace(".json", ""));
                    });
        } catch (IOException e) {
            logger.error("Failed to list transaction sessions", e);
        }

        return sessions;
    }

    public String getCurrentSessionId() {
        return currentSessionId;
    }

    public List<OperationRecord> getCurrentSession() {
        return new ArrayList<>(currentSession);
    }

    /**
     * Wrapper class for JSON serialization
     */
    @Setter
    @Getter
    public static class TransactionSession {
        private String sessionId;
        private Date timestamp;
        private int totalOperations;
        private List<OperationRecord> operations;
    }
}
