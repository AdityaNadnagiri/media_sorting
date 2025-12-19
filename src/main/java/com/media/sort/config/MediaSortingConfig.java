package com.media.sort.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Enhanced configuration properties for media sorting with flexible options.
 * Uses Lombok @Data to eliminate boilerplate getters/setters.
 */
@Data
@Component
@ConfigurationProperties(prefix = "media")
public class MediaSortingConfig {

    // Folder structure
    private String folderPattern = "{year}/{year-month}/{device}/{extension}";

    // Date strategy
    private DateStrategy dateStrategy = DateStrategy.GPS_PREFERRED;

    // Duplicate handling
    private DuplicateStrategy duplicateStrategy = DuplicateStrategy.KEEP_LARGEST;

    // Date validation
    private int minYear = 2000;
    private int maxYear = 2030;

    // Performance
    private Processing processing = new Processing();

    // Error handling
    private ErrorHandling errorHandling = new ErrorHandling();

    // Caching
    private Cache cache = new Cache();

    // Geocoding
    private Geocoding geocoding = new Geocoding();

    // Perceptual hashing
    private PerceptualHash perceptualHash = new PerceptualHash();

    public enum DateStrategy {
        EXIF_ONLY, // Only use EXIF dates
        FILESYSTEM_FALLBACK, // Use EXIF, fall back to filesystem
        GPS_PREFERRED, // Prefer GPS > EXIF > Filesystem
        SMART_VALIDATION // Cross-validate dates for accuracy
    }

    public enum DuplicateStrategy {
        KEEP_LARGEST, // Keep file with largest size
        KEEP_OLDEST, // Keep file with oldest date
        KEEP_NEWEST, // Keep file with newest date
        KEEP_BOTH, // Don't delete any duplicates
        ASK_USER // Prompt for each duplicate
    }

    @Data
    public static class Processing {
        private boolean parallel = true;
        private int threads = Runtime.getRuntime().availableProcessors();
        private int batchSize = 100;
    }

    @Data
    public static class ErrorHandling {
        private boolean createSpecialFolders = true;
        private boolean haltOnError = false;
    }

    @Data
    public static class Cache {
        private boolean enabled = true;
        private int maxEntries = 10000;
        private int ttlHours = 24;
    }

    @Data
    public static class Geocoding {
        private boolean enabled = false;
        private String provider = "NOMINATIM"; // NOMINATIM or GOOGLE_MAPS
        private boolean cacheEnabled = true;
        private int rateLimitMs = 1000;
        private String apiKey = "";
    }

    @Data
    public static class PerceptualHash {
        private boolean enabled = false;
        private double threshold = 0.95; // 0.0 to 1.0
        private String algorithm = "PHASH"; // PHASH, DHASH, AVERAGE_HASH
    }
}
