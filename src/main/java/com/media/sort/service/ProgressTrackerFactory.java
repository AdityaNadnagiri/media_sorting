package com.media.sort.service;

import com.media.sort.MediaSortingProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class ProgressTrackerFactory {

    @Autowired
    private MediaSortingProperties properties;

    private final ConcurrentMap<String, ProgressTracker> trackers = new ConcurrentHashMap<>();

    /**
     * Gets the log directory set by LogDirectoryInitializer
     */
    private synchronized String getRunLogDirectory() {
        // LOG_DIR is already set by LogDirectoryInitializer during app startup
        return System.getProperty("LOG_DIR", "logs/run_default");
    }

    /**
     * Prepends the run-specific directory to a log file path
     */
    private String getRunSpecificPath(String logFilePath) {
        if (logFilePath == null) {
            return getRunLogDirectory() + "/default.log";
        }

        // Remove "logs/" prefix if present to avoid duplication
        String cleanPath = logFilePath.startsWith("logs/") ? logFilePath.substring(5) : logFilePath;
        return getRunLogDirectory() + "/" + cleanPath;
    }

    /**
     * Gets or creates a ProgressTracker for the specified log file path
     * 
     * @param logFilePath the path to the log file
     * @return a shared ProgressTracker instance
     */
    public ProgressTracker getOrCreateTracker(String logFilePath) {
        String runSpecificPath = getRunSpecificPath(logFilePath);
        return trackers.computeIfAbsent(runSpecificPath, ProgressTracker::new);
    }

    /**
     * Gets a ProgressTracker for common log types using configuration
     */
    public ProgressTracker getImageErrorTracker() {
        return getOrCreateTracker(properties.getLogFilePaths().getImageErrorLogPath());
    }

    public ProgressTracker getVideoErrorTracker() {
        return getOrCreateTracker(properties.getLogFilePaths().getVideoErrorLogPath());
    }

    public ProgressTracker getMediaErrorTracker() {
        return getOrCreateTracker(properties.getLogFilePaths().getMediaErrorLogPath());
    }

    public ProgressTracker getPhotoOrganizerErrorTracker() {
        return getOrCreateTracker(properties.getLogFilePaths().getPhotoOrganizerErrorLogPath());
    }

    public ProgressTracker getFileComparisonTracker() {
        return getOrCreateTracker(properties.getLogFilePaths().getFileComparisonLogPath());
    }

    public ProgressTracker getCleanupTracker() {
        return getOrCreateTracker(properties.getLogFilePaths().getEmptyFolderCleanupLogPath());
    }

    public ProgressTracker getFolderComparisonTracker() {
        return getOrCreateTracker(properties.getLogFilePaths().getFolderComparisonProgressLogPath());
    }

    // Additional video-specific trackers
    public ProgressTracker getVideoMp4ErrorTracker() {
        return getOrCreateTracker(properties.getLogFilePaths().getVideoMp4ErrorLogPath());
    }

    public ProgressTracker getVideoTgpErrorTracker() {
        return getOrCreateTracker(properties.getLogFilePaths().getVideoTgpErrorLogPath());
    }

    public ProgressTracker getVideoQtErrorTracker() {
        return getOrCreateTracker(properties.getLogFilePaths().getVideoQtErrorLogPath());
    }

    public ProgressTracker getVideoOtherErrorTracker() {
        return getOrCreateTracker(properties.getLogFilePaths().getVideoOtherErrorLogPath());
    }
}
