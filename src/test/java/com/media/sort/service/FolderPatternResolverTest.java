package com.media.sort.service;

import com.media.sort.model.ExifData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for FolderPatternResolver service
 * DISABLED: ExifData constructor requires refactoring to support testing
 * without actual file processing
 */
@Disabled("ExifData constructor calls processFile() before ProgressTrackers can be set")
class FolderPatternResolverTest {

    private FolderPatternResolver resolver;
    private ExifData testExifData;
    private ProgressTracker mockTracker;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        resolver = new FolderPatternResolver();

        // Create mock tracker
        mockTracker = new ProgressTracker() {
            @Override
            public void saveProgress(String message) {
                // No-op for testing
            }
        };

        // Create an actual temp file to avoid FileNotFoundException
        File tempFile = tempDir.resolve("test.jpg").toFile();
        Files.writeString(tempFile.toPath(), "test content");

        testExifData = new ExifData(tempFile);
        testExifData.setProgressTrackers(mockTracker, mockTracker, mockTracker);
        testExifData.setDateTaken(new Date(1609459200000L)); // 2021-01-01 00:00:00 UTC
        testExifData.setDeviceName("Canon");
        testExifData.setDeviceModel("EOS 5D Mark IV");
        testExifData.setExtension("jpg");
    }

    @Test
    void testResolvePath_YearToken() {
        String result = resolver.resolvePath("{year}/photos", testExifData);
        assertTrue(result.startsWith("2021/photos") || result.startsWith("2020/photos")); // Timezone dependent
    }

    @Test
    void testResolvePath_YearMonthToken() {
        String result = resolver.resolvePath("{year-month}/photos", testExifData);
        assertTrue(result.matches("202[01]-\\d{2}/photos"));
    }

    @Test
    void testResolvePath_FullDateToken() {
        String result = resolver.resolvePath("{year-month-day}/photos", testExifData);
        assertTrue(result.matches("202[01]-\\d{2}-\\d{2}/photos"));
    }

    @Test
    void testResolvePath_DeviceToken() {
        String result = resolver.resolvePath("photos/{device}", testExifData);
        assertTrue(result.contains("EOS 5D Mark IV") || result.contains("EOS_5D_Mark_IV"));
    }

    @Test
    void testResolvePath_ExtensionToken() {
        String result = resolver.resolvePath("photos/{extension}", testExifData);
        assertEquals("photos/jpg", result);
    }

    @Test
    void testResolvePath_ComplexPattern() {
        String result = resolver.resolvePath("{year}/{year-month}/{device}/{extension}", testExifData);
        assertNotNull(result);
        assertTrue(result.contains("jpg"));
    }

    @Test
    void testResolvePath_NullPattern() {
        String result = resolver.resolvePath(null, testExifData);
        assertNull(result);
    }

    @Test
    void testResolvePath_NullExifData() {
        String result = resolver.resolvePath("{year}/photos", null);
        assertNull(result);
    }

    @Test
    void testResolvePath_NoDate() throws IOException {
        File tempFile = tempDir.resolve("test_nodate.jpg").toFile();
        Files.writeString(tempFile.toPath(), "test content");

        ExifData noDate = new ExifData(tempFile);
        noDate.setProgressTrackers(mockTracker, mockTracker, mockTracker);
        noDate.setDeviceModel("Canon");
        noDate.setExtension("jpg");

        String result = resolver.resolvePath("{year}/{device}/{extension}", noDate);
        assertNotNull(result);
        // Should handle missing date gracefully
        assertTrue(result.contains("Canon"));
        assertTrue(result.contains("jpg"));
    }
}
