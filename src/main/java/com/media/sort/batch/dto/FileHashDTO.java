package com.media.sort.batch.dto;

import com.media.sort.model.ExifData;

import java.io.File;
import java.nio.file.Path;

/**
 * Enhanced DTO for file hash information with metadata.
 * Used in folder comparison to store file info and EXIF data.
 */
public class FileHashDTO {

    private Path filePath;
    private String hash;
    private File file;
    private ExifData exifData;
    private long fileSize;

    public FileHashDTO() {
    }

    public FileHashDTO(Path filePath, String hash) {
        this.filePath = filePath;
        this.hash = hash;
        this.file = filePath.toFile();
        this.fileSize = file.length();
    }

    public FileHashDTO(Path filePath, String hash, ExifData exifData) {
        this.filePath = filePath;
        this.hash = hash;
        this.file = filePath.toFile();
        this.exifData = exifData;
        this.fileSize = file.length();
    }

    public Path getFilePath() {
        return filePath;
    }

    public void setFilePath(Path filePath) {
        this.filePath = filePath;
        if (filePath != null) {
            this.file = filePath.toFile();
            this.fileSize = file.length();
        }
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public File getFile() {
        return file;
    }

    public void setFile(File file) {
        this.file = file;
        if (file != null) {
            this.filePath = file.toPath();
            this.fileSize = file.length();
        }
    }

    public ExifData getExifData() {
        return exifData;
    }

    public void setExifData(ExifData exifData) {
        this.exifData = exifData;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }
}
