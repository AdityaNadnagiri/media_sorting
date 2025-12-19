package com.media.sort.model;

import lombok.Data;
import java.util.HashMap;
import java.util.Map;

/**
 * Comprehensive report of processing results.
 * Uses Lombok @Data for getters/setters while preserving custom business
 * methods.
 */
@Data
public class ProcessingReport {

    private long totalFilesProcessed;
    private long successfullyOrganized;
    private long errorsOccurred;

    // Date source breakdown
    private long gpsDateCount;
    private long exifDateCount;
    private long filesystemDateCount;
    private long noDateCount;

    // Special handling
    private long corruptExifCount;
    private long manualReviewCount;
    private long unsupportedFormatCount;

    // Duplicates
    private long exactDuplicatesCount;
    private long perceptualDuplicatesCount;
    private long burstPhotosCount;

    // Performance
    private long startTime;
    private long endTime;
    private long totalBytesProcessed;
    private int cacheHitCount;
    private int cacheMissCount;

    // Errors (Note: some duplicate fields for compatibility)
    private int exifReadErrors;
    private int fileMoveErrors;
    private long duplicatesFound;
    private long errorCount;
    private int cacheHits;
    private int cacheMisses;

    // Lists for detailed tracking
    private Map<String, Long> extensionCounts;
    private Map<String, Long> deviceCounts;

    public ProcessingReport() {
        this.startTime = System.currentTimeMillis();
        this.extensionCounts = new HashMap<>();
        this.deviceCounts = new HashMap<>();
    }

    // ========== Custom Business Methods ==========

    public void markComplete() {
        this.endTime = System.currentTimeMillis();
    }

    public long getDurationSeconds() {
        return (endTime - startTime) / 1000;
    }

    public long getProcessingTimeMs() {
        return endTime - startTime;
    }

    public double getFilesPerSecond() {
        long duration = getDurationSeconds();
        if (duration == 0)
            return 0;
        return (double) totalFilesProcessed / duration;
    }

    public double getCacheHitRate() {
        int total = cacheHitCount + cacheMissCount;
        if (total == 0)
            return 0;
        return (double) cacheHitCount / total * 100;
    }

    public void incrementExtensionCount(String extension) {
        extensionCounts.merge(extension.toLowerCase(), 1L, Long::sum);
    }

    public void incrementDeviceCount(String device) {
        if (device != null && !device.isEmpty()) {
            deviceCounts.merge(device, 1L, Long::sum);
        }
    }

    // Increment methods
    public void incrementTotalFilesProcessed() {
        this.totalFilesProcessed++;
    }

    public void incrementSuccessfullyOrganized() {
        successfullyOrganized++;
    }

    public void incrementDuplicatesFound() {
        duplicatesFound++;
    }

    public void incrementErrorCount() {
        errorCount++;
    }

    public void incrementErrorsOccurred() {
        this.errorsOccurred++;
    }

    public void incrementGpsDateCount() {
        this.gpsDateCount++;
    }

    public void incrementExifDateCount() {
        this.exifDateCount++;
    }

    public void incrementFilesystemDateCount() {
        this.filesystemDateCount++;
    }

    public void incrementNoDateCount() {
        this.noDateCount++;
    }

    public void incrementCorruptExifCount() {
        this.corruptExifCount++;
    }

    public void incrementManualReviewCount() {
        this.manualReviewCount++;
    }

    public void incrementUnsupportedFormatCount() {
        this.unsupportedFormatCount++;
    }

    public void incrementCacheHit() {
        this.cacheHitCount++;
        this.cacheHits++; // Also update compatibility field
    }

    public void incrementCacheMiss() {
        this.cacheMissCount++;
        this.cacheMisses++; // Also update compatibility field
    }

    public void addBytesProcessed(long bytes) {
        this.totalBytesProcessed += bytes;
    }
}
