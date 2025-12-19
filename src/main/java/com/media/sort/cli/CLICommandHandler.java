package com.media.sort.cli;

import com.media.sort.service.TransactionLog;
import com.media.sort.service.UndoService;
import com.media.sort.service.UndoService.UndoResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Scanner;

/**
 * Handler for CLI commands (--undo, --list-sessions, etc.)
 */
@Component
public class CLICommandHandler {

    private static final Logger logger = LoggerFactory.getLogger(CLICommandHandler.class);

    @Autowired
    private UndoService undoService;

    @Autowired
    private TransactionLog transactionLog;

    /**
     * Handle --undo command
     */
    public boolean handleUndoCommand(String sessionId, String baseDirectory) {
        if (sessionId == null || sessionId.isEmpty()) {
            logger.error("Session ID required for undo command");
            logger.info("Usage: --undo [session-id]");
            logger.info("Use --list-sessions to see available sessions");
            return false;
        }

        logger.info("=".repeat(80));
        logger.info("UNDO OPERATION");
        logger.info("=".repeat(80));
        logger.info("Session ID: {}", sessionId);

        // Confirm with user
        if (!confirmUndo()) {
            logger.info("Undo cancelled by user");
            return false;
        }

        // Perform undo
        UndoResult result = undoService.undoSession(sessionId, baseDirectory);

        // Display results
        logger.info("");
        logger.info("UNDO RESULTS:");
        logger.info("  Success: {}", result.isSuccess());
        logger.info("  Operations reversed: {}", result.getSuccessCount());
        logger.info("  Failed reversals: {}", result.getFailCount());

        if (result.getErrorMessage() != null) {
            logger.error("Errors occurred:");
            logger.error(result.getErrorMessage());
        }

        logger.info("=".repeat(80));

        return result.isSuccess();
    }

    /**
     * Handle --list-sessions command
     */
    public void handleListSessionsCommand(String baseDirectory) {
        List<String> sessions = transactionLog.listSessions(baseDirectory);

        logger.info("=".repeat(80));
        logger.info("AVAILABLE TRANSACTION SESSIONS");
        logger.info("=".repeat(80));

        if (sessions.isEmpty()) {
            logger.info("No transaction sessions found");
        } else {
            logger.info("Found {} session(s):", sessions.size());
            for (int i = 0; i < sessions.size(); i++) {
                logger.info("  {}. {}", i + 1, sessions.get(i));
            }
            logger.info("");
            logger.info("To undo a session, use: --undo [session-id]");
        }

        logger.info("=".repeat(80));
    }

    /**
     * Confirm undo operation with user
     */
    private boolean confirmUndo() {
        try (Scanner scanner = new Scanner(System.in)) {
            System.out.print("Are you sure you want to undo this operation? (yes/no): ");
            String response = scanner.nextLine().trim().toLowerCase();
            return response.equals("yes") || response.equals("y");
        }
    }
}
