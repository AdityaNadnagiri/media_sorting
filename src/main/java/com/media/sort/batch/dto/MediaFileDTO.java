package com.media.sort.batch.dto;

import com.media.sort.model.ExifData;
import java.io.File;

/**
 * Data Transfer Object for media file processing in batch jobs.
 * Immutable record with automatic getters, equals, hashCode, toString.
 */
public record MediaFileDTO(
        File sourceFile,
        String targetPath,
        ExifData exifData,
        String fileHash,
        MediaType mediaType) {
    public enum MediaType {
        IMAGE, VIDEO
    }
}
