package com.media.sort.service;

import com.media.sort.model.ExifData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test for FileQualityComparator to verify the new priority rule:
 * Files with BOTH higher resolution AND larger file size should be
 * prioritized as original regardless of dates.
 */
class FileQualityComparatorTest {

        private FileQualityComparator comparator;

        @TempDir
        Path tempDir;

        @BeforeEach
        void setUp() {
                comparator = new FileQualityComparator();
        }

        @Test
        void testBothHigherResolutionAndLargerFileSize_RelaxesDateRule() throws IOException {
                // Create two files with different sizes
                File largerFile = createTempFile("larger.jpg", 1730951); // ~1.65 MB
                File smallerFile = createTempFile("smaller.jpg", 845489); // ~0.81 MB

                // Create mock EXIF data
                ExifData largerExif = mock(ExifData.class);
                ExifData smallerExif = mock(ExifData.class);

                // Larger file has higher resolution (4032x3024 vs 2016x1512)
                when(largerExif.getWidth()).thenReturn(4032);
                when(largerExif.getHeight()).thenReturn(3024);
                when(smallerExif.getWidth()).thenReturn(2016);
                when(smallerExif.getHeight()).thenReturn(1512);

                // Smaller file has OLDER date (would normally win)
                Date olderDate = Date.from(LocalDateTime.of(2020, 1, 30, 22, 20, 58)
                                .atZone(ZoneId.systemDefault()).toInstant());
                Date newerDate = Date.from(LocalDateTime.of(2020, 10, 6, 19, 46, 32)
                                .atZone(ZoneId.systemDefault()).toInstant());

                when(smallerExif.getDateTaken()).thenReturn(olderDate);
                when(largerExif.getDateTaken()).thenReturn(newerDate);
                when(smallerExif.isAfter(largerExif)).thenReturn(false); // smaller is older
                when(largerExif.isAfter(smallerExif)).thenReturn(true); // larger is newer

                // Test: Larger file should be prioritized despite having newer date
                // because it has BOTH higher resolution AND larger file size
                boolean result = comparator.isFile1HigherQuality(largerFile, smallerFile, largerExif, smallerExif);

                assertTrue(result, "File with BOTH higher resolution AND larger size should be original, " +
                                "even with newer date");
        }

        @Test
        void testOnlyHigherResolution_DoesNotRelaxDateRule() throws IOException {
                // Create two files with SAME size
                File file1 = createTempFile("file1.jpg", 1000000);
                File file2 = createTempFile("file2.jpg", 1000000); // Same size

                ExifData exif1 = mock(ExifData.class);
                ExifData exif2 = mock(ExifData.class);

                // File1 has higher resolution
                when(exif1.getWidth()).thenReturn(4032);
                when(exif1.getHeight()).thenReturn(3024);
                when(exif2.getWidth()).thenReturn(2016);
                when(exif2.getHeight()).thenReturn(1512);

                // File2 has older date
                Date olderDate = Date.from(LocalDateTime.of(2020, 1, 30, 22, 20, 58)
                                .atZone(ZoneId.systemDefault()).toInstant());
                Date newerDate = Date.from(LocalDateTime.of(2020, 10, 6, 19, 46, 32)
                                .atZone(ZoneId.systemDefault()).toInstant());

                when(exif2.getDateTaken()).thenReturn(olderDate);
                when(exif1.getDateTaken()).thenReturn(newerDate);

                // Test: Higher resolution alone should still win (existing behavior)
                boolean result = comparator.isFile1HigherQuality(file1, file2, exif1, exif2);

                assertTrue(result, "Higher resolution should win even with same file size");
        }

        @Test
        void testOnlyLargerFileSize_FollowsDateRule() throws IOException {
                // Create two files with different sizes
                File largerFile = createTempFile("larger.jpg", 2000000);
                File smallerFile = createTempFile("smaller.jpg", 1000000);

                ExifData largerExif = mock(ExifData.class);
                ExifData smallerExif = mock(ExifData.class);

                // SAME resolution
                when(largerExif.getWidth()).thenReturn(4032);
                when(largerExif.getHeight()).thenReturn(3024);
                when(smallerExif.getWidth()).thenReturn(4032);
                when(smallerExif.getHeight()).thenReturn(3024);

                // Smaller file has older date
                Date olderDate = Date.from(LocalDateTime.of(2020, 1, 30, 22, 20, 58)
                                .atZone(ZoneId.systemDefault()).toInstant());
                Date newerDate = Date.from(LocalDateTime.of(2020, 10, 6, 19, 46, 32)
                                .atZone(ZoneId.systemDefault()).toInstant());

                when(smallerExif.getDateTaken()).thenReturn(olderDate);
                when(largerExif.getDateTaken()).thenReturn(newerDate);
                when(smallerExif.isAfter(largerExif)).thenReturn(false);
                when(largerExif.isAfter(smallerExif)).thenReturn(true);

                // Test: Date rule should still apply (older file wins)
                boolean result = comparator.isFile1HigherQuality(largerFile, smallerFile, largerExif, smallerExif);

                assertFalse(result, "When only file size differs (not resolution), date rule should apply");
        }

        @Test
        void testCopyPatternFileCanBeOriginal_WhenItHasOlderDate() throws IOException {
                // This test validates the fix for the issue where DSC00251(1).JPG is the
                // original
                // and DSC00251.JPG is the copy
                File fileWithPattern = createTempFile("DSC00251(1).JPG", 1000000);
                File fileWithoutPattern = createTempFile("DSC00251.JPG", 1000000);

                // Create REAL ExifData objects instead of mocks
                // The isBetterQualityThan method accesses fields directly, not through getters
                ExifData exifWithPattern = new ExifData();
                ExifData exifWithoutPattern = new ExifData();

                // Set file references
                exifWithPattern.setFile(fileWithPattern);
                exifWithoutPattern.setFile(fileWithoutPattern);

                // Set same resolution
                exifWithPattern.setImageWidth(3024);
                exifWithPattern.setImageHeight(4032);
                exifWithoutPattern.setImageWidth(3024);
                exifWithoutPattern.setImageHeight(4032);

                // Set same file size
                exifWithPattern.setFileSize(1000000L);
                exifWithoutPattern.setFileSize(1000000L);

                // File with (1) has OLDER date - it's the original!
                Date olderDate = Date.from(LocalDateTime.of(2009, 9, 24, 10, 15, 0)
                                .atZone(ZoneId.systemDefault()).toInstant());
                Date newerDate = Date.from(LocalDateTime.of(2009, 9, 25, 14, 30, 0)
                                .atZone(ZoneId.systemDefault()).toInstant());

                // Set dates directly on fields (not just mocking getters)
                exifWithPattern.setDateTaken(olderDate);
                exifWithPattern.setDateCreated(olderDate);
                exifWithPattern.setDateModified(olderDate);

                exifWithoutPattern.setDateTaken(newerDate);
                exifWithoutPattern.setDateCreated(newerDate);
                exifWithoutPattern.setDateModified(newerDate);

                // Test using ExifData.isBetterQualityThan directly since it now prioritizes
                // dates
                boolean result = exifWithPattern.isBetterQualityThan(exifWithoutPattern);

                assertTrue(result, "File with (1) pattern should be recognized as original when it has older date");
        }

        private File createTempFile(String name, long size) throws IOException {
                Path filePath = tempDir.resolve(name);
                byte[] content = new byte[(int) size];
                Files.write(filePath, content);
                return filePath.toFile();
        }
}
