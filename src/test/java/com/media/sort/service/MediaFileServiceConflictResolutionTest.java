package com.media.sort.service;

import com.media.sort.model.ExifData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for MediaFileService conflict resolution logic.
 * Validates that files with both higher resolution and larger file size
 * are correctly identified as originals during conflict detection.
 */
class MediaFileServiceConflictResolutionTest {

        @TempDir
        Path tempDir;

        private FileQualityComparator fileQualityComparator;
        private ProgressTracker mockTracker;

        @BeforeEach
        void setUp() {
                fileQualityComparator = new FileQualityComparator();
                // Create a simple mock tracker that does nothing
                mockTracker = new ProgressTracker() {
                        @Override
                        public void saveProgress(String message) {
                                // No-op for testing
                        }
                };
                // Note: For full integration test, you'd need to inject all dependencies
                // This is a simplified unit test focusing on the comparison logic
        }

        /**
         * Helper to initialize ExifData with mock trackers to avoid
         * NullPointerException
         */
        private void initializeExifData(ExifData exifData) {
                exifData.setProgressTrackers(mockTracker, mockTracker, mockTracker);
        }

        /**
         * Creates a test image file with specified dimensions and color
         */
        private File createTestImage(String filename, int width, int height, Color color) throws IOException {
                Path imagePath = tempDir.resolve(filename);

                BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                Graphics2D g2d = image.createGraphics();
                g2d.setColor(color);
                g2d.fillRect(0, 0, width, height);
                g2d.dispose();

                ImageIO.write(image, "jpg", imagePath.toFile());
                return imagePath.toFile();
        }

        @Test
        void testConflictResolution_LargerHigherResolutionFile_BecomesOriginal() throws IOException {
                // Create two images with different resolutions
                File smallerFile = createTestImage("small.jpg", 1330, 2365, Color.BLUE); // ~3.1 MP
                File largerFile = createTestImage("large.jpg", 4032, 3024, Color.RED); // ~12.2 MP

                // Verify file sizes (larger resolution should have larger file size)
                assertTrue(largerFile.length() > smallerFile.length(),
                                "Higher resolution file should be larger in bytes");

                // Create ExifData objects
                ExifData smallerExif = new ExifData(smallerFile);
                ExifData largerExif = new ExifData(largerFile);
                initializeExifData(smallerExif);
                initializeExifData(largerExif);

                // Manually set dimensions (simulating ImageIO extraction)
                smallerExif.setImageWidth(1330);
                smallerExif.setImageHeight(2365);
                largerExif.setImageWidth(4032);
                largerExif.setImageHeight(3024);

                // Test: Larger file should be considered higher quality
                boolean largerIsHigherQuality = fileQualityComparator.isFile1HigherQuality(
                                largerFile, smallerFile, largerExif, smallerExif);

                assertTrue(largerIsHigherQuality,
                                "File with both higher resolution and larger size should be higher quality");
        }

        @Test
        void testConflictResolution_SameResolution_DateTakesPrecedence() throws IOException {
                // Create two images with same resolution
                File file1 = createTestImage("file1.jpg", 2000, 1500, Color.BLUE);
                File file2 = createTestImage("file2.jpg", 2000, 1500, Color.RED);

                // Make file1 older by setting last modified time
                file1.setLastModified(System.currentTimeMillis() - 10000);
                file2.setLastModified(System.currentTimeMillis());

                ExifData exif1 = new ExifData(file1);
                ExifData exif2 = new ExifData(file2);
                initializeExifData(exif1);
                initializeExifData(exif2);

                exif1.setImageWidth(2000);
                exif1.setImageHeight(1500);
                exif2.setImageWidth(2000);
                exif2.setImageHeight(1500);

                // Test: When resolutions are equal, date comparison should apply
                // File1 (older) should be considered higher quality
                boolean file2IsHigherQuality = fileQualityComparator.isFile1HigherQuality(
                                file2, file1, exif2, exif1);

                assertFalse(file2IsHigherQuality,
                                "When resolutions are equal, older file should be considered original");
        }

        @Test
        void testConflictResolution_OnlyHigherResolution_StillWins() throws IOException {
                // Create files where higher resolution has smaller file size
                // (This can happen with different compression levels)
                File lowerResFile = createTestImage("lower.jpg", 1000, 750, Color.BLUE);
                File higherResFile = createTestImage("higher.jpg", 3000, 2250, Color.RED);

                ExifData lowerExif = new ExifData(lowerResFile);
                ExifData higherExif = new ExifData(higherResFile);
                initializeExifData(lowerExif);
                initializeExifData(higherExif);

                lowerExif.setImageWidth(1000);
                lowerExif.setImageHeight(750);
                higherExif.setImageWidth(3000);
                higherExif.setImageHeight(2250);

                // Test: Higher resolution should win even if file size is different
                boolean higherResIsHigherQuality = fileQualityComparator.isFile1HigherQuality(
                                higherResFile, lowerResFile, higherExif, lowerExif);

                assertTrue(higherResIsHigherQuality,
                                "Higher resolution file should be considered higher quality");
        }

        @Test
        void testConflictResolution_MissingResolutionData_FallbackToDate() throws IOException {
                // Create two files
                File file1 = createTestImage("file1.jpg", 2000, 1500, Color.BLUE);
                File file2 = createTestImage("file2.jpg", 2000, 1500, Color.RED);

                file1.setLastModified(System.currentTimeMillis() - 10000);
                file2.setLastModified(System.currentTimeMillis());

                ExifData exif1 = new ExifData(file1);
                ExifData exif2 = new ExifData(file2);
                initializeExifData(exif1);
                initializeExifData(exif2);

                // Don't set image dimensions - simulating missing resolution data
                // exif1.setImageWidth/Height not called
                // exif2.setImageWidth/Height not called

                // Test: When resolution data is missing, should fall back to date comparison
                boolean file2IsHigherQuality = fileQualityComparator.isFile1HigherQuality(
                                file2, file1, exif2, exif1);

                assertFalse(file2IsHigherQuality,
                                "When resolution data is missing, older file should be considered original");
        }

        @Test
        void testImageIO_ExtractsDimensionsCorrectly() throws IOException {
                // Test that our ImageIO approach correctly extracts dimensions
                int expectedWidth = 1920;
                int expectedHeight = 1080;

                File testImage = createTestImage("test.jpg", expectedWidth, expectedHeight, Color.GREEN);

                // Use ImageIO to read dimensions
                BufferedImage img = ImageIO.read(testImage);
                assertNotNull(img, "ImageIO should successfully read the image");

                assertEquals(expectedWidth, img.getWidth(), "Width should match");
                assertEquals(expectedHeight, img.getHeight(), "Height should match");

                // Create ExifData and set dimensions
                ExifData exifData = new ExifData(testImage);
                exifData.setImageWidth(img.getWidth());
                exifData.setImageHeight(img.getHeight());

                assertEquals(expectedWidth, exifData.getWidth(), "ExifData width should match");
                assertEquals(expectedHeight, exifData.getHeight(), "ExifData height should match");
        }

        @Test
        void testConflictResolution_SpecialRule_BothHigherResAndLargerSize() throws IOException {
                // This tests the special rule: BOTH higher resolution AND larger file size

                // Small file: 800x600
                File smallFile = createTestImage("small.jpg", 800, 600, Color.BLUE);

                // Large file: 3200x2400 (4x the pixels)
                File largeFile = createTestImage("large.jpg", 3200, 2400, Color.RED);

                // Make small file older (normally would win on date)
                smallFile.setLastModified(System.currentTimeMillis() - 20000);
                largeFile.setLastModified(System.currentTimeMillis());

                ExifData smallExif = new ExifData(smallFile);
                ExifData largeExif = new ExifData(largeFile);
                initializeExifData(smallExif);
                initializeExifData(largeExif);

                smallExif.setImageWidth(800);
                smallExif.setImageHeight(600);
                largeExif.setImageWidth(3200);
                largeExif.setImageHeight(2400);

                // Verify preconditions
                assertTrue(largeFile.length() > smallFile.length(),
                                "Large file should have larger file size");
                assertTrue(largeExif.getWidth() * largeExif.getHeight() > smallExif.getWidth() * smallExif.getHeight(),
                                "Large file should have higher resolution");

                // Test: Large file should win despite being newer (special rule)
                boolean largeIsHigherQuality = fileQualityComparator.isFile1HigherQuality(
                                largeFile, smallFile, largeExif, smallExif);

                assertTrue(largeIsHigherQuality,
                                "File with BOTH higher resolution AND larger size should override date rules");
        }
}
