package com.media.sort.service;

import com.media.sort.model.ProcessingReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

/**
 * Service to generate comprehensive processing reports
 */
@Service
public class ReportingService {

    private static final Logger logger = LoggerFactory.getLogger(ReportingService.class);
    private static final String REPORTS_DIR = "reports";

    private ProcessingReport currentReport;

    /**
     * Start a new report session
     */
    public void startReport() {
        this.currentReport = new ProcessingReport();
        logger.info("Processing report started");
    }

    /**
     * Get the current report
     */
    public ProcessingReport getCurrentReport() {
        return currentReport;
    }

    /**
     * Generate and save final report
     */
    public void finalizeReport(String baseDirectory) {
        if (currentReport == null) {
            logger.warn("No active report to finalize");
            return;
        }

        currentReport.markComplete();

        // Print console summary
        printConsoleSummary();

        // Save to file
        try {
            saveReportToFile(baseDirectory);
        } catch (IOException e) {
            logger.error("Failed to save report to file", e);
        }
    }

    /**
     * Print report summary to console
     */
    private void printConsoleSummary() {
        logger.info("=".repeat(80));
        logger.info("MEDIA SORTING REPORT");
        logger.info("=".repeat(80));
        logger.info("Date: {}", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        logger.info("Duration: {} seconds ({} minutes)",
                currentReport.getDurationSeconds(),
                currentReport.getDurationSeconds() / 60);

        logger.info("");
        logger.info("FILES PROCESSED:");
        logger.info("   Total: {}", currentReport.getTotalFilesProcessed());
        logger.info("   Successfully organized: {}", currentReport.getSuccessfullyOrganized());
        logger.info("   Errors: {}", currentReport.getErrorsOccurred());

        logger.info("");
        logger.info("DATE SOURCE BREAKDOWN:");
        logger.info("  - GPS-dated: {} files", currentReport.getGpsDateCount());
        logger.info("  - EXIF-dated: {} files", currentReport.getExifDateCount());
        logger.info("  - Filesystem-dated: {} files", currentReport.getFilesystemDateCount());
        logger.info("  - No date: {} files", currentReport.getNoDateCount());

        if (currentReport.getCorruptExifCount() > 0 ||
                currentReport.getManualReviewCount() > 0 ||
                currentReport.getUnsupportedFormatCount() > 0) {
            logger.info("");
            logger.info("SPECIAL HANDLING:");
            if (currentReport.getCorruptExifCount() > 0) {
                logger.info("  ⚠ Corrupt EXIF: {} files → /_CorruptEXIF/",
                        currentReport.getCorruptExifCount());
            }
            if (currentReport.getManualReviewCount() > 0) {
                logger.info("  ⚠ Manual review: {} files → /_ManualReviewRequired/",
                        currentReport.getManualReviewCount());
            }
            if (currentReport.getUnsupportedFormatCount() > 0) {
                logger.info("  ⚠ Unsupported: {} files → /_UnsupportedFormat/",
                        currentReport.getUnsupportedFormatCount());
            }
        }

        if (currentReport.getExactDuplicatesCount() > 0 ||
                currentReport.getPerceptualDuplicatesCount() > 0) {
            logger.info("");
            logger.info("DUPLICATES:");
            logger.info("  - Exact duplicates: {} pairs", currentReport.getExactDuplicatesCount());
            logger.info("  - Perceptual duplicates: {} pairs", currentReport.getPerceptualDuplicatesCount());
            if (currentReport.getBurstPhotosCount() > 0) {
                logger.info("  - Burst photos (kept): {} sequences", currentReport.getBurstPhotosCount());
            }
        }

        logger.info("");
        logger.info("PERFORMANCE:");
        logger.info("  - Average speed: {:.1f} files/second", currentReport.getFilesPerSecond());
        logger.info("  - Total data: {}", formatBytes(currentReport.getTotalBytesProcessed()));
        if (currentReport.getCacheHitCount() + currentReport.getCacheMissCount() > 0) {
            logger.info("  - Cache hit rate: {:.1f}%", currentReport.getCacheHitRate());
        }

        if (currentReport.getExifReadErrors() > 0 || currentReport.getFileMoveErrors() > 0) {
            logger.info("");
            logger.info("ERRORS:");
            if (currentReport.getExifReadErrors() > 0) {
                logger.info("  - EXIF read errors: {}", currentReport.getExifReadErrors());
            }
            if (currentReport.getFileMoveErrors() > 0) {
                logger.info("  - File move errors: {}", currentReport.getFileMoveErrors());
            }
        }

        // Extension breakdown
        if (!currentReport.getExtensionCounts().isEmpty()) {
            logger.info("");
            logger.info("FILE TYPES:");
            currentReport.getExtensionCounts().entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(10)
                    .forEach(entry -> logger.info("  - {}: {} files",
                            entry.getKey(), entry.getValue()));
        }

        // Device breakdown
        if (!currentReport.getDeviceCounts().isEmpty()) {
            logger.info("");
            logger.info("DEVICES:");
            currentReport.getDeviceCounts().entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .forEach(entry -> logger.info("  - {}: {} files",
                            entry.getKey(), entry.getValue()));
        }

        logger.info("=".repeat(80));
    }

    /**
     * Generate and print console report
     */
    public void generateConsoleReport() {
        if (currentReport == null) {
            logger.warn("No active report to generate");
            return;
        }

        printConsoleSummary();
    }

    /**
     * Save report to text file
     */
    private void saveReportToFile(String baseDirectory) throws IOException {
        Path reportsDir = Paths.get(baseDirectory, REPORTS_DIR);
        Files.createDirectories(reportsDir);

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HHmmss");
        String filename = "report_" + sdf.format(new Date()) + ".txt";
        Path reportPath = reportsDir.resolve(filename);

        StringBuilder report = new StringBuilder();
        report.append("=".repeat(80)).append("\n");
        report.append("MEDIA SORTING REPORT\n");
        report.append("=".repeat(80)).append("\n");
        report.append("Generated: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()))
                .append("\n");
        report.append("Duration: ").append(currentReport.getDurationSeconds()).append(" seconds\n");
        report.append("\n");

        // Add all statistics
        report.append("SUMMARY:\n");
        report.append("  Total files: ").append(currentReport.getTotalFilesProcessed()).append("\n");
        report.append("  Successfully organized: ").append(currentReport.getSuccessfullyOrganized()).append("\n");
        report.append("  Errors: ").append(currentReport.getErrorsOccurred()).append("\n");
        report.append("\n");

        report.append("DATE SOURCES:\n");
        report.append("  GPS:        ").append(currentReport.getGpsDateCount()).append("\n");
        report.append("  EXIF:       ").append(currentReport.getExifDateCount()).append("\n");
        report.append("  Filesystem: ").append(currentReport.getFilesystemDateCount()).append("\n");
        report.append("  No date:    ").append(currentReport.getNoDateCount()).append("\n");
        report.append("\n");

        Files.writeString(reportPath, report.toString());
        logger.info("Report saved: {}", reportPath);
    }

    /**
     * Format bytes to human-readable format
     */
    private String formatBytes(long bytes) {
        if (bytes < 1024)
            return bytes + " B";
        if (bytes < 1024 * 1024)
            return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024)
            return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
