package com.media.sort.service;

import com.media.sort.model.OperationRecord;
import com.media.sort.service.UndoService.UndoResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for UndoService
 */
class UndoServiceTest {

    private UndoService undoService;
    private TransactionLog transactionLog;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        transactionLog = new TransactionLog();
        undoService = new UndoService();
        // Inject the transaction log into undo service
        undoService.setTransactionLog(transactionLog);
    }

    @Test
    void testUndoSuccessfulMove() throws IOException {
        // Create test files
        Path sourceFile = tempDir.resolve("source.jpg");
        Path destFile = tempDir.resolve("dest.jpg");
        Files.writeString(sourceFile, "test content");

        // Simulate a move
        Files.move(sourceFile, destFile);

        // Create transaction log
        transactionLog.startSession(tempDir.toString());
        OperationRecord operation = new OperationRecord(
                OperationRecord.OperationType.MOVE,
                sourceFile,
                destFile);
        operation.setCompleted(true);
        transactionLog.logOperation(operation);
        transactionLog.saveSession();

        String sessionId = transactionLog.getCurrentSessionId();

        // Perform undo
        UndoResult result = undoService.undoSession(sessionId, tempDir.toString());

        assertTrue(result.isSuccess());
        assertEquals(1, result.getSuccessCount());
        assertEquals(0, result.getFailCount());
        assertTrue(Files.exists(sourceFile));
        assertFalse(Files.exists(destFile));
    }

    @Test
    void testUndoWithMissingFile() throws IOException {
        // Create transaction log with non-existent file
        transactionLog.startSession(tempDir.toString());
        OperationRecord operation = new OperationRecord(
                OperationRecord.OperationType.MOVE,
                Paths.get(tempDir.toString(), "nonexistent-source.jpg"),
                Paths.get(tempDir.toString(), "nonexistent-dest.jpg"));
        operation.setCompleted(true);
        transactionLog.logOperation(operation);
        transactionLog.saveSession();

        String sessionId = transactionLog.getCurrentSessionId();

        // Perform undo
        UndoResult result = undoService.undoSession(sessionId, tempDir.toString());

        assertFalse(result.isSuccess());
        assertEquals(0, result.getSuccessCount());
        assertEquals(1, result.getFailCount());
        assertNotNull(result.getErrorMessage());
    }

    @Test
    void testUndoInvalidSession() {
        UndoResult result = undoService.undoSession("invalid-session", tempDir.toString());

        assertFalse(result.isSuccess());
        assertNotNull(result.getErrorMessage());
    }

    @Test
    void testUndoMultipleOperations() throws IOException {
        // Create test files
        Path source1 = tempDir.resolve("file1.jpg");
        Path dest1 = tempDir.resolve("organized/file1.jpg");
        Path source2 = tempDir.resolve("file2.jpg");
        Path dest2 = tempDir.resolve("organized/file2.jpg");

        Files.writeString(source1, "content1");
        Files.writeString(source2, "content2");
        Files.createDirectories(dest1.getParent());
        Files.move(source1, dest1);
        Files.move(source2, dest2);

        // Log operations
        transactionLog.startSession(tempDir.toString());
        transactionLog.logOperation(new OperationRecord(
                OperationRecord.OperationType.MOVE, source1, dest1).setCompleted(true));
        transactionLog.logOperation(new OperationRecord(
                OperationRecord.OperationType.MOVE, source2, dest2).setCompleted(true));
        transactionLog.saveSession();

        // Undo
        UndoResult result = undoService.undoSession(
                transactionLog.getCurrentSessionId(), tempDir.toString());

        assertTrue(result.isSuccess());
        assertEquals(2, result.getSuccessCount());
        assertTrue(Files.exists(source1));
        assertTrue(Files.exists(source2));
    }
}
