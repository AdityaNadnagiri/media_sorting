package com.media.sort.service;

import com.media.sort.model.ProcessingReport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for ReportingService
 */
class ReportingServiceTest {

    private ReportingService reportingService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        reportingService = new ReportingService();
    }

    @Test
    void testStartReport() {
        reportingService.startReport();

        ProcessingReport report = reportingService.getCurrentReport();
        assertNotNull(report);
        assertNotNull(report.getStartTime());
    }

    @Test
    void testIncrementCounters() {
        reportingService.startReport();

        reportingService.getCurrentReport().incrementSuccessfullyOrganized();
        reportingService.getCurrentReport().incrementSuccessfullyOrganized();
        reportingService.getCurrentReport().incrementDuplicatesFound();

        ProcessingReport report = reportingService.getCurrentReport();
        assertEquals(2, report.getSuccessfullyOrganized());
        assertEquals(1, report.getDuplicatesFound());
    }

    @Test
    void testExtensionCounting() {
        reportingService.startReport();

        reportingService.getCurrentReport().incrementExtensionCount("jpg");
        reportingService.getCurrentReport().incrementExtensionCount("jpg");
        reportingService.getCurrentReport().incrementExtensionCount("png");

        ProcessingReport report = reportingService.getCurrentReport();
        assertEquals(2, report.getExtensionCounts().get("jpg"));
        assertEquals(1, report.getExtensionCounts().get("png"));
    }

    @Test
    void testCacheStatistics() {
        reportingService.startReport();

        reportingService.getCurrentReport().incrementCacheHit();
        reportingService.getCurrentReport().incrementCacheHit();
        reportingService.getCurrentReport().incrementCacheMiss();

        ProcessingReport report = reportingService.getCurrentReport();
        assertEquals(2, report.getCacheHits());
        assertEquals(1, report.getCacheMisses());
    }

    @Test
    void testFinalizeReport() throws IOException {
        reportingService.startReport();
        reportingService.getCurrentReport().incrementSuccessfullyOrganized();

        reportingService.finalizeReport(tempDir.toString());

        ProcessingReport report = reportingService.getCurrentReport();
        assertNotNull(report.getEndTime());
        assertTrue(report.getProcessingTimeMs() >= 0); // Can be 0 if test runs very fast

        // Check that text report was created
        Path reportsDir = tempDir.resolve("reports");
        assertTrue(Files.exists(reportsDir));
        assertTrue(Files.list(reportsDir).anyMatch(p -> p.toString().endsWith(".txt")));
    }

    @Test
    void testGenerateConsoleReport() {
        reportingService.startReport();
        reportingService.getCurrentReport().incrementSuccessfullyOrganized();
        reportingService.getCurrentReport().incrementDuplicatesFound();
        reportingService.getCurrentReport().incrementErrorCount();

        // This should not throw exception
        assertDoesNotThrow(() -> reportingService.generateConsoleReport());
    }
}
