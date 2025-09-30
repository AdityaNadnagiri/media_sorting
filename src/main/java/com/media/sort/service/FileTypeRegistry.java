package com.media.sort.service;

import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Service
public class FileTypeRegistry {
    
    public static final Set<String> IMAGE_EXTENSIONS = new HashSet<>(Arrays.asList(
        "arw", "jpg", "jpeg", "gif", "bmp", "ico", "tif", "tiff", "raw", "indd", 
        "ai", "eps", "pdf", "heic", "cr2", "nrw", "k25"
    ));
    
    public static final Set<String> VIDEO_EXTENSIONS = new HashSet<>(Arrays.asList(
        "mp4", "mkv", "flv", "avi", "mov", "wmv", "rm", "mpg", "mpeg", 
        "3gp", "vob", "m4v", "3g2", "divx", "xvid"
    ));
    
    public boolean isImage(String extension) {
        return IMAGE_EXTENSIONS.contains(extension.toLowerCase());
    }
    
    public boolean isVideo(String extension) {
        return VIDEO_EXTENSIONS.contains(extension.toLowerCase());
    }
    
    public String getFileType(String extension) {
        String ext = extension.toLowerCase();
        if (IMAGE_EXTENSIONS.contains(ext)) {
            return "image";
        } else if (VIDEO_EXTENSIONS.contains(ext)) {
            return "video";
        } else {
            return "other";
        }
    }
}