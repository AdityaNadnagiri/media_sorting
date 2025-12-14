package com.media.sort.batch.dto;

import com.media.sort.model.ExifData;

import java.io.File;

/**
 * Data Transfer Object for media file processing in batch jobs.
 * Contains source file, target path, EXIF data, and metadata.
 */
public class MediaFileDTO {

    private File sourceFile;
    private String targetPath;
    private ExifData exifData;
    private String fileHash;
    private MediaType mediaType;

    public enum MediaType {
        IMAGE, VIDEO
    }

    public MediaFileDTO() {
    }

    public MediaFileDTO(File sourceFile, String targetPath, ExifData exifData, String fileHash, MediaType mediaType) {
        this.sourceFile = sourceFile;
        this.targetPath = targetPath;
        this.exifData = exifData;
        this.fileHash = fileHash;
        this.mediaType = mediaType;
    }

    // Getters and setters

    public File getSourceFile() {
        return sourceFile;
    }

    public void setSourceFile(File sourceFile) {
        this.sourceFile = sourceFile;
    }

    public String getTargetPath() {
        return targetPath;
    }

    public void setTargetPath(String targetPath) {
        this.targetPath = targetPath;
    }

    public ExifData getExifData() {
        return exifData;
    }

    public void setExifData(ExifData exifData) {
        this.exifData = exifData;
    }

    public String getFileHash() {
        return fileHash;
    }

    public void setFileHash(String fileHash) {
        this.fileHash = fileHash;
    }

    public MediaType getMediaType() {
        return mediaType;
    }

    public void setMediaType(MediaType mediaType) {
        this.mediaType = mediaType;
    }
}
