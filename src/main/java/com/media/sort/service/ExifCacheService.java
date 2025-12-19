package com.media.sort.service;

import com.media.sort.config.MediaSortingConfig;
import com.media.sort.model.FileCacheEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * LRU Cache service for EXIF data to avoid re-reading unchanged files
 */
@Service
public class ExifCacheService {

    private static final Logger logger = LoggerFactory.getLogger(ExifCacheService.class);

    @Autowired
    private MediaSortingConfig config;

    private Map<String, FileCacheEntry> cache;

    public ExifCacheService() {
        // Initialize will happen post-construct
    }

    /**
     * Initialize cache with LRU eviction policy
     */
    public void init(int maxEntries) {
        this.cache = new LinkedHashMap<String, FileCacheEntry>(maxEntries + 1, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, FileCacheEntry> eldest) {
                return size() > maxEntries;
            }
        };
        logger.info("EXIF cache initialized with max {} entries", maxEntries);
    }

    /**
     * Get cached EXIF data if available and valid
     */
    public Object get(String filePath, long fileSize, long lastModified) {
        if (!config.getCache().isEnabled()) {
            return null;
        }

        if (cache == null) {
            init(config.getCache().getMaxEntries());
        }

        String key = FileCacheEntry.generateKey(filePath, fileSize, lastModified);
        FileCacheEntry entry = cache.get(key);

        if (entry == null) {
            return null;
        }

        // Check if cache entry is still valid
        if (!entry.isValid(config.getCache().getTtlHours())) {
            cache.remove(key);
            return null;
        }

        logger.debug("Cache hit: {}", filePath);
        return entry.getExifData();
    }

    /**
     * Store EXIF data in cache
     */
    public void put(String filePath, long fileSize, long lastModified, Object exifData) {
        if (!config.getCache().isEnabled()) {
            return;
        }

        if (cache == null) {
            init(config.getCache().getMaxEntries());
        }

        String key = FileCacheEntry.generateKey(filePath, fileSize, lastModified);
        cache.put(key, new FileCacheEntry(filePath, fileSize, lastModified, exifData));
        logger.debug("Cache stored: {}", filePath);
    }

    /**
     * Clear all cache entries
     */
    public void clear() {
        if (cache != null) {
            cache.clear();
            logger.info("Cache cleared");
        }
    }

    /**
     * Get cache statistics
     */
    public int getSize() {
        return cache != null ? cache.size() : 0;
    }
}
