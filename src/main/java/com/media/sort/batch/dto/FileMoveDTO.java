package com.media.sort.batch.dto;

import com.media.sort.model.ExifData;

import java.nio.file.Path;

/**
 * Enhanced DTO for file move operations with quality comparison info.
 * Indicates which file is original and which is duplicate.
 */
public class FileMoveDTO {

    private Path sourcePath;
    private Path targetPath;
    private Path referencePath;

    // Quality comparison info
    private boolean sourceIsOriginal; // true if source is higher quality
    private ExifData sourceExif;
    private ExifData referenceExif;

    public FileMoveDTO() {
    }

    public FileMoveDTO(Path sourcePath, Path targetPath, Path referencePath) {
        this.sourcePath = sourcePath;
        this.targetPath = targetPath;
        this.referencePath = referencePath;
        this.sourceIsOriginal = false; // default: reference is original
    }

    public FileMoveDTO(Path sourcePath, Path targetPath, Path referencePath,
            boolean sourceIsOriginal, ExifData sourceExif, ExifData referenceExif) {
        this.sourcePath = sourcePath;
        this.targetPath = targetPath;
        this.referencePath = referencePath;
        this.sourceIsOriginal = sourceIsOriginal;
        this.sourceExif = sourceExif;
        this.referenceExif = referenceExif;
    }

    public Path getSourcePath() {
        return sourcePath;
    }

    public void setSourcePath(Path sourcePath) {
        this.sourcePath = sourcePath;
    }

    public Path getTargetPath() {
        return targetPath;
    }

    public void setTargetPath(Path targetPath) {
        this.targetPath = targetPath;
    }

    public Path getReferencePath() {
        return referencePath;
    }

    public void setReferencePath(Path referencePath) {
        this.referencePath = referencePath;
    }

    public boolean isSourceIsOriginal() {
        return sourceIsOriginal;
    }

    public void setSourceIsOriginal(boolean sourceIsOriginal) {
        this.sourceIsOriginal = sourceIsOriginal;
    }

    public ExifData getSourceExif() {
        return sourceExif;
    }

    public void setSourceExif(ExifData sourceExif) {
        this.sourceExif = sourceExif;
    }

    public ExifData getReferenceExif() {
        return referenceExif;
    }

    public void setReferenceExif(ExifData referenceExif) {
        this.referenceExif = referenceExif;
    }
}
