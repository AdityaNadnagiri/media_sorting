package com.media.sort.batch.dto;

import com.media.sort.model.ExifData;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.File;
import java.nio.file.Path;

/**
 * Enhanced DTO for file hash information with metadata.
 * Used in folder comparison to store file info and EXIF data.
 * Uses Lombok @Data to reduce boilerplate while preserving custom setter logic.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileHashDTO {

    private Path filePath;
    private String hash;

    @Setter(AccessLevel.NONE) // Custom setter below
    private File file;

    private ExifData exifData;

    // Perceptual hash for detecting visually similar images
    private String perceptualHash;

    @Setter(AccessLevel.NONE) // Computed in setFilePath/setFile
    private long fileSize;

    /**
     * Custom setter for filePath that also updates file and fileSize
     */
    public void setFilePath(Path filePath) {
        this.filePath = filePath;
        if (filePath != null) {
            this.file = filePath.toFile();
            this.fileSize = file.length();
        }
    }

    /**
     * Custom setter for file that also updates filePath and fileSize
     */
    public void setFile(File file) {
        this.file = file;
        if (file != null) {
            this.filePath = file.toPath();
            this.fileSize = file.length();
        }
    }
}
