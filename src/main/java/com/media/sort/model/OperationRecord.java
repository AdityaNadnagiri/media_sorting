package com.media.sort.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.nio.file.Path;
import java.util.Date;

/**
 * Record of a single file operation for undo capability.
 * Uses Lombok to reduce boilerplate while preserving custom constructors and
 * methods.
 */
@Data
@NoArgsConstructor
public class OperationRecord {

    public enum OperationType {
        MOVE,
        COPY,
        DELETE,
        CREATE_FOLDER
    }

    private String operationId;
    private Date timestamp;
    private OperationType type;
    private Path sourcePath;
    private Path destinationPath;
    private String exifHash; // To verify integrity
    private Long fileSize;
    private boolean completed;
    private String errorMessage;

    public OperationRecord(OperationType type, Path source, Path destination) {
        this();
        this.timestamp = new Date();
        this.completed = false;
        this.type = type;
        this.sourcePath = source;
        this.destinationPath = destination;
        this.operationId = generateOperationId();
    }

    private String generateOperationId() {
        return type + "_" + System.currentTimeMillis() + "_" + Math.random();
    }

    /**
     * Fluent setter for completed status (preserves builder pattern)
     */
    public OperationRecord setCompleted(boolean completed) {
        this.completed = completed;
        return this;
    }
}
