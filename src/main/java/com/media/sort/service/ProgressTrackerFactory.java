package com.media.sort.service;

import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class ProgressTrackerFactory {
    
    private final ConcurrentMap<String, ProgressTracker> trackers = new ConcurrentHashMap<>();
    
    /**
     * Gets or creates a ProgressTracker for the specified log file path
     * @param logFilePath the path to the log file
     * @return a shared ProgressTracker instance
     */
    public ProgressTracker getOrCreateTracker(String logFilePath) {
        return trackers.computeIfAbsent(logFilePath, ProgressTracker::new);
    }
    
    /**
     * Gets a ProgressTracker for common log types
     */
    public ProgressTracker getImageErrorTracker() {
        return getOrCreateTracker("logs/po/image/error.txt");
    }
    
    public ProgressTracker getVideoErrorTracker() {
        return getOrCreateTracker("logs/po/video/error.txt");
    }
    
    public ProgressTracker getMediaErrorTracker() {
        return getOrCreateTracker("logs/po/media/error.txt");
    }
    
    public ProgressTracker getPhotoOrganizerErrorTracker() {
        return getOrCreateTracker("logs/po/error.txt");
    }
    
    public ProgressTracker getFileComparisonTracker() {
        return getOrCreateTracker("logs/po/file/compare.txt");
    }
    
    public ProgressTracker getCleanupTracker() {
        return getOrCreateTracker("logs/cleanup/empty-folders.txt");
    }
    
    public ProgressTracker getFolderComparisonTracker() {
        return getOrCreateTracker("logs/compare/progress.txt");
    }
}