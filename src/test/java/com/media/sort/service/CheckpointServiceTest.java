package com.media.sort.service;

import com.media.sort.service.CheckpointService.Checkpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CheckpointService
 */
class CheckpointServiceTest {

    private CheckpointService checkpointService;
    private Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        checkpointService = new CheckpointService();
        tempDir = Files.createTempDirectory("checkpoint-test");
    }

    @Test
    void testSaveCheckpoint_CreatesFile() {
        Checkpoint checkpoint = new Checkpoint();
        checkpoint.setTotalCount(100);
        checkpoint.addProcessedFile("file1.jpg");
        checkpoint.addProcessedFile("file2.jpg");

        checkpointService.saveCheckpoint(tempDir.toString(), checkpoint);

        assertTrue(checkpointService.hasCheckpoint(tempDir.toString()));
    }

    @Test
    void testLoadCheckpoint_RestoresData() {
        // Create and save checkpoint
        Checkpoint original = new Checkpoint();
        original.setTotalCount(100);
        original.addProcessedFile("file1.jpg");
        original.addProcessedFile("file2.jpg");

        checkpointService.saveCheckpoint(tempDir.toString(), original);

        // Load checkpoint
        Checkpoint loaded = checkpointService.loadCheckpoint(tempDir.toString());

        assertNotNull(loaded);
        assertEquals(100, loaded.getTotalCount());
        assertEquals(2, loaded.getProcessedCount());
        assertTrue(loaded.isFileProcessed("file1.jpg"));
        assertTrue(loaded.isFileProcessed("file2.jpg"));
    }

    @Test
    void testLoadCheckpoint_NoCheckpointExists() {
        Checkpoint loaded = checkpointService.loadCheckpoint(tempDir.toString());
        assertNull(loaded);
    }

    @Test
    void testDeleteCheckpoint() {
        // Create checkpoint
        Checkpoint checkpoint = new Checkpoint();
        checkpoint.setTotalCount(100);
        checkpointService.saveCheckpoint(tempDir.toString(), checkpoint);

        assertTrue(checkpointService.hasCheckpoint(tempDir.toString()));

        // Delete it
        checkpointService.deleteCheckpoint(tempDir.toString());

        assertFalse(checkpointService.hasCheckpoint(tempDir.toString()));
    }

    @Test
    void testCheckpoint_ProgressCalculation() {
        Checkpoint checkpoint = new Checkpoint();
        checkpoint.setTotalCount(100);

        assertEquals(0, checkpoint.getProgress());

        for (int i = 0; i < 50; i++) {
            checkpoint.addProcessedFile("file" + i + ".jpg");
        }

        assertEquals(50, checkpoint.getProgress());
    }

    @Test
    void testCheckpoint_IsFileProcessed() {
        Checkpoint checkpoint = new Checkpoint();
        checkpoint.addProcessedFile("processed.jpg");

        assertTrue(checkpoint.isFileProcessed("processed.jpg"));
        assertFalse(checkpoint.isFileProcessed("not-processed.jpg"));
    }
}
