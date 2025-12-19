package com.media.sort.model;

import java.util.Date;

/**
 * Cache entry for storing EXIF data to avoid re-reading unchanged files
 */
public class FileCacheEntry {

    private final String filePath;
    private final long fileSize;
    private final long lastModified;
    private final Object exifData; // Stored EXIF data
    private final Date cacheTime;

    public FileCacheEntry(String filePath, long fileSize, long lastModified, Object exifData) {
        this.filePath = filePath;
        this.fileSize = fileSize;
        this.lastModified = lastModified;
        this.exifData = exifData;
        this.cacheTime = new Date();
    }

    /**
     * Generate cache key from file metadata
     */
    public static String generateKey(String path, long size, long modified) {
        return path + "_" + size + "_" + modified;
    }

    /**
     * Check if cache entry is still valid
     */
    public boolean isValid(int ttlHours) {
        long ageMs = System.currentTimeMillis() - cacheTime.getTime();
        long ttlMs = ttlHours * 60 * 60 * 1000L;
        return ageMs < ttlMs;
    }

    public String getFilePath() {
        return filePath;
    }

    public long getFileSize() {
        return fileSize;
    }

    public long getLastModified() {
        return lastModified;
    }

    public Object getExifData() {
        return exifData;
    }

    public Date getCacheTime() {
        return cacheTime;
    }
}
