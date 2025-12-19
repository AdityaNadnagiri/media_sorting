package com.media.sort.service;

import com.media.sort.model.OperationRecord;
import com.media.sort.service.TransactionLog.TransactionSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TransactionLog service
 */
class TransactionLogTest {

    private TransactionLog transactionLog;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        transactionLog = new TransactionLog();
    }

    @Test
    void testStartSession_CreatesTransactionDirectory() {
        transactionLog.startSession(tempDir.toString());

        Path transactionDir = tempDir.resolve("transactions");
        assertTrue(Files.exists(transactionDir));
        assertTrue(Files.isDirectory(transactionDir));
    }

    @Test
    void testLogOperation_SingleOperation() {
        transactionLog.startSession(tempDir.toString());

        OperationRecord operation = new OperationRecord(
                OperationRecord.OperationType.MOVE,
                tempDir.resolve("source.jpg"),
                tempDir.resolve("dest.jpg"));
        operation.setCompleted(true);

        transactionLog.logOperation(operation);

        List<OperationRecord> session = transactionLog.getCurrentSession();
        assertEquals(1, session.size());
        assertEquals(operation, session.get(0));
    }

    @Test
    void testSaveSession_CreatesJsonFile() throws Exception {
        transactionLog.startSession(tempDir.toString());

        OperationRecord operation = new OperationRecord(
                OperationRecord.OperationType.MOVE,
                tempDir.resolve("source.jpg"),
                tempDir.resolve("dest.jpg"));

        transactionLog.logOperation(operation);
        transactionLog.saveSession();

        // Check that JSON file was created
        Path transactionDir = tempDir.resolve("transactions");
        assertTrue(Files.list(transactionDir).anyMatch(p -> p.toString().endsWith(".json")));
    }

    @Test
    void testLoadSession_RestoresOperations() {
        // Start and save a session
        transactionLog.startSession(tempDir.toString());
        String sessionId = transactionLog.getCurrentSessionId();

        OperationRecord operation = new OperationRecord(
                OperationRecord.OperationType.MOVE,
                tempDir.resolve("source.jpg"),
                tempDir.resolve("dest.jpg"));
        operation.setFileSize(1024L);

        transactionLog.logOperation(operation);
        transactionLog.saveSession();

        // Load the session
        TransactionSession loaded = transactionLog.loadSession(sessionId, tempDir.toString());

        assertNotNull(loaded);
        assertEquals(sessionId, loaded.getSessionId());
        assertEquals(1, loaded.getTotalOperations());
        assertEquals(1, loaded.getOperations().size());
    }

    @Test
    void testListSessions_ReturnsAvailableSessions() {
        // Create multiple sessions
        transactionLog.startSession(tempDir.toString());
        transactionLog.logOperation(new OperationRecord(
                OperationRecord.OperationType.MOVE,
                tempDir.resolve("file1.jpg"),
                tempDir.resolve("dest1.jpg")));
        transactionLog.saveSession();

        List<String> sessions = transactionLog.listSessions(tempDir.toString());
        assertEquals(1, sessions.size());
    }

    @Test
    void testAutoSave_EveryTenOperations() {
        transactionLog.startSession(tempDir.toString());

        // Add 10 operations
        for (int i = 0; i < 10; i++) {
            OperationRecord operation = new OperationRecord(
                    OperationRecord.OperationType.MOVE,
                    tempDir.resolve("source" + i + ".jpg"),
                    tempDir.resolve("dest" + i + ".jpg"));
            transactionLog.logOperation(operation);
        }

        // Auto-save should have triggered
        Path transactionDir = tempDir.resolve("transactions");
        assertTrue(Files.exists(transactionDir));
    }
}
