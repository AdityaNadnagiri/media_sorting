package com.media.sort.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service for preview mode - shows what would happen without actually moving
 * files
 */
@Service
public class PreviewModeService {

    private static final Logger logger = LoggerFactory.getLogger(PreviewModeService.class);

    @Value("${media.preview-mode.enabled:false}")
    private boolean previewMode;

    private int plannedMoves = 0;
    private int plannedDuplicates = 0;

    /**
     * Log a planned file move in preview mode
     */
    public void logPlannedMove(String sourcePath, String destPath, boolean isDuplicate) {
        if (!previewMode) {
            return;
        }

        if (isDuplicate) {
            plannedDuplicates++;
            logger.info("[PREVIEW] Would move DUPLICATE: {} -> {}", sourcePath, destPath);
        } else {
            plannedMoves++;
            logger.info("[PREVIEW] Would move ORIGINAL: {} -> {}", sourcePath, destPath);
        }
    }

    /**
     * Print summary of planned operations
     */
    public void printSummary() {
        if (!previewMode) {
            return;
        }

        logger.info("=".repeat(80));
        logger.info("PREVIEW MODE SUMMARY");
        logger.info("=".repeat(80));
        logger.info("Planned original moves: {}", plannedMoves);
        logger.info("Planned duplicate moves: {}", plannedDuplicates);
        logger.info("Total operations: {}", plannedMoves + plannedDuplicates);
        logger.info("=".repeat(80));
        logger.info("No files were actually moved (preview mode)");
    }

    /**
     * Reset counters
     */
    public void reset() {
        plannedMoves = 0;
        plannedDuplicates = 0;
    }

    public boolean isPreviewMode() {
        return previewMode;
    }

    public void setPreviewMode(boolean previewMode) {
        this.previewMode = previewMode;
    }
}
