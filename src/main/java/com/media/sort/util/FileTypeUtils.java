package com.media.sort.util;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Utility class for file type detection and management
 */
public final class FileTypeUtils {
    
    public static final Set<String> IMAGE_EXTENSIONS = new HashSet<>(Arrays.asList(
        "arw", "jpg", "jpeg", "gif", "bmp", "ico", "tif", "tiff", "raw", "indd", 
        "ai", "eps", "pdf", "heic", "cr2", "nrw", "k25"
    ));
    
    public static final Set<String> VIDEO_EXTENSIONS = new HashSet<>(Arrays.asList(
        "mp4", "mkv", "flv", "avi", "mov", "wmv", "rm", "mpg", "mpeg", 
        "3gp", "vob", "m4v", "3g2", "divx", "xvid"
    ));
    
    private FileTypeUtils() {
        // Utility class - prevent instantiation
    }
    
    /**
     * Determines if a file extension represents an image file
     */
    public static boolean isImage(String extension) {
        return IMAGE_EXTENSIONS.contains(extension.toLowerCase());
    }
    
    /**
     * Determines if a file extension represents a video file
     */
    public static boolean isVideo(String extension) {
        return VIDEO_EXTENSIONS.contains(extension.toLowerCase());
    }
    
    /**
     * Gets the file type based on extension
     * @param extension file extension (with or without dot)
     * @return "image", "video", or "other"
     */
    public static String getFileType(String extension) {
        String ext = extension.startsWith(".") ? extension.substring(1) : extension;
        ext = ext.toLowerCase();
        
        if (IMAGE_EXTENSIONS.contains(ext)) {
            return "image";
        } else if (VIDEO_EXTENSIONS.contains(ext)) {
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
}