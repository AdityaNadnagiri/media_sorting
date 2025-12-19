package com.media.sort;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.Set;

/**
 * Configuration properties for Media Sorting Application.
 * Binds to properties with prefix "app.media-sorting" in
 * application.properties.
 * Uses Lombok @Data to eliminate boilerplate getters/setters.
 */
@Data
@ConfigurationProperties(prefix = "app.media-sorting")
public class MediaSortingProperties {

    // Main source folder for media processing
    private String sourceFolder;

    // Root logs directory
    private String rootLogsFolder;

    // Feature flags
    private boolean enableDeviceFolderCreation;
    private boolean enableDuplicateMoving;
    private boolean perceptualHashEnabled;
    private boolean enableCrossRunDuplicateDetection;

    // Directory structure configuration
    private DirectoryStructure directoryStructure = new DirectoryStructure();

    // Log file paths configuration
    private LogFilePaths logFilePaths = new LogFilePaths();

    // File type extensions configuration
    private FileExtensions fileExtensions = new FileExtensions();

    // Batch job properties for folder comparison
    private BatchJobProperties batchJob = new BatchJobProperties();

    @Data
    public static class DirectoryStructure {
        private String emptyFolderDirectoryName;
        private String imagesDirectoryName;
        private String videosDirectoryName;
        private String originalSubDirectoryName;
        private String duplicateSubDirectoryName;
        private String othersDirectoryName;
    }

    @Data
    public static class LogFilePaths {
        private String imageErrorLogPath;
        private String videoErrorLogPath;
        private String mediaErrorLogPath;
        private String photoOrganizerErrorLogPath;
        private String fileComparisonLogPath;
        private String emptyFolderCleanupLogPath;
        private String folderComparisonProgressLogPath;
        private String videoMp4ErrorLogPath;
        private String videoTgpErrorLogPath;
        private String videoQtErrorLogPath;
        private String videoOtherErrorLogPath;
    }

    @Data
    public static class FileExtensions {
        private Set<String> supportedImageExtensions;
        private Set<String> supportedVideoExtensions;
    }

    @Data
    public static class BatchJobProperties {
        private String primaryFolderPath;
        private String secondaryFolderPath;
        private int maxThreadPoolSize;
        private String comparisonLogsDirectoryPath;
    }
}