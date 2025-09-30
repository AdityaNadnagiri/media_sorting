package com.media.sort.util;

import com.media.sort.MediaSortingProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Utility class for file type detection and management
 * Now uses MediaSortingProperties for configurable file extensions
 */
@Component
public class FileTypeUtils {
    
    private static MediaSortingProperties properties;
    
    @Autowired
    public void setProperties(MediaSortingProperties properties) {
        FileTypeUtils.properties = properties;
    }
    
    /**
     * Determines if a file extension represents an image file
     */
    public static boolean isImage(String extension) {
        if (properties != null) {
            return properties.getFileExtensions().getSupportedImageExtensions()
                    .contains(extension.toLowerCase());
        }
        // Fallback to default set if properties not available
        return getDefaultImageExtensions().contains(extension.toLowerCase());
    }
    
    /**
     * Determines if a file extension represents a video file
     */
    public static boolean isVideo(String extension) {
        if (properties != null) {
            return properties.getFileExtensions().getSupportedVideoExtensions()
                    .contains(extension.toLowerCase());
        }
        // Fallback to default set if properties not available
        return getDefaultVideoExtensions().contains(extension.toLowerCase());
    }
    
    /**
     * Gets the file type based on extension
     * @param extension file extension (with or without dot)
     * @return "image", "video", or "other"
     */
    public static String getFileType(String extension) {
        String ext = extension.startsWith(".") ? extension.substring(1) : extension;
        ext = ext.toLowerCase();
        
        if (isImage(ext)) {
            return "image";
        } else if (isVideo(ext)) {
            return "video";
        } else {
            return "other";
        }
    }
    
    /**
     * Extracts the file extension from a filename
     */
    public static String getExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        return (dotIndex == -1) ? "" : filename.substring(dotIndex + 1);
    }
    
    /**
     * Default image extensions for fallback
     */
    private static Set<String> getDefaultImageExtensions() {
        return Set.of("arw", "jpg", "jpeg", "gif", "bmp", "ico", "tif", "tiff", 
                     "raw", "indd", "ai", "eps", "pdf", "heic", "cr2", "nrw", 
                     "k25", "png", "webp");
    }
    
    /**
     * Default video extensions for fallback
     */
    private static Set<String> getDefaultVideoExtensions() {
        return Set.of("mp4", "mkv", "flv", "avi", "mov", "wmv", "rm", "mpg", 
                     "mpeg", "3gp", "vob", "m4v", "3g2", "divx", "xvid", "webm");
    }
}