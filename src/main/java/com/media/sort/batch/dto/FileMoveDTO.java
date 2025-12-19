package com.media.sort.batch.dto;

import com.media.sort.model.ExifData;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.nio.file.Path;

/**
 * DTO for file move operations with quality comparison.
 * Uses Lombok @Data and @Builder for flexible construction with defaults.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileMoveDTO {

    private Path sourcePath;
    private Path targetPath;
    private Path referencePath;

    @Builder.Default
    private boolean sourceIsOriginal = false;

    private ExifData sourceExif;
    private ExifData referenceExif;
}
