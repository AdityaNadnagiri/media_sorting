package com.media.sort.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for DuplicatePatternUtils
 */
class DuplicatePatternUtilsTest {

    // ===== Burst Detection Tests =====

    @Test
    void testIsBurstSequence_Sequential() {
        assertTrue(DuplicatePatternUtils.isBurstSequence("IMG_0146.JPG", "IMG_0147.JPG"));
        assertTrue(DuplicatePatternUtils.isBurstSequence("DSC03215.JPG", "DSC03216.JPG"));
        assertTrue(DuplicatePatternUtils.isBurstSequence("Photo_001.jpg", "Photo_002.jpg"));
    }

    @Test
    void testIsBurstSequence_NotSequential() {
        assertFalse(DuplicatePatternUtils.isBurstSequence("IMG_001.JPG", "IMG_003.JPG"));
        assertFalse(DuplicatePatternUtils.isBurstSequence("IMG_100.JPG", "IMG_200.JPG"));
    }

    @Test
    void testIsBurstSequence_NoNumbers() {
        assertFalse(DuplicatePatternUtils.isBurstSequence("Photo.JPG", "Image.JPG"));
    }

    @Test
    void testIsBurstSequence_NullInputs() {
        assertFalse(DuplicatePatternUtils.isBurstSequence(null, "IMG_001.JPG"));
        assertFalse(DuplicatePatternUtils.isBurstSequence("IMG_001.JPG", null));
        assertFalse(DuplicatePatternUtils.isBurstSequence(null, null));
    }

    // ===== RAW Format Detection Tests =====

    @Test
    void testIsRawFormat_CanonFormats() {
        assertTrue(DuplicatePatternUtils.isRawFormat("IMG_001.CR2"));
        assertTrue(DuplicatePatternUtils.isRawFormat("img_001.cr2"));
    }

    @Test
    void testIsRawFormat_SonyFormats() {
        assertTrue(DuplicatePatternUtils.isRawFormat("DSC_1234.ARW"));
        assertTrue(DuplicatePatternUtils.isRawFormat("dsc_1234.arw"));
    }

    @Test
    void testIsRawFormat_NikonFormats() {
        assertTrue(DuplicatePatternUtils.isRawFormat("DSC_1234.NEF"));
    }

    @Test
    void testIsRawFormat_UniversalFormats() {
        assertTrue(DuplicatePatternUtils.isRawFormat("IMG_001.DNG"));
    }

    @Test
    void testIsRawFormat_OtherFormats() {
        assertTrue(DuplicatePatternUtils.isRawFormat("IMG_001.ORF")); // Olympus
        assertTrue(DuplicatePatternUtils.isRawFormat("IMG_001.RAF")); // Fuji
        assertTrue(DuplicatePatternUtils.isRawFormat("IMG_001.RW2")); // Panasonic
        assertTrue(DuplicatePatternUtils.isRawFormat("IMG_001.PEF")); // Pentax
    }

    @Test
    void testIsRawFormat_NotRaw() {
        assertFalse(DuplicatePatternUtils.isRawFormat("IMG_001.JPG"));
        assertFalse(DuplicatePatternUtils.isRawFormat("IMG_001.PNG"));
        assertFalse(DuplicatePatternUtils.isRawFormat("IMG_001.HEIC"));
    }

    @Test
    void testIsRawFormat_NullInput() {
        assertFalse(DuplicatePatternUtils.isRawFormat(null));
    }

    // ===== RAW+JPEG Pairing Tests =====

    @Test
    void testIsRawJpegPair_ValidPairs() {
        assertTrue(DuplicatePatternUtils.isRawJpegPair("IMG_001.CR2", "IMG_001.JPG"));
        assertTrue(DuplicatePatternUtils.isRawJpegPair("IMG_001.JPG", "IMG_001.CR2")); // Order shouldn't matter
        assertTrue(DuplicatePatternUtils.isRawJpegPair("DSC_1234.NEF", "DSC_1234.JPEG"));
        assertTrue(DuplicatePatternUtils.isRawJpegPair("Photo.DNG", "Photo.jpg"));
    }

    @Test
    void testIsRawJpegPair_DifferentBasenames() {
        assertFalse(DuplicatePatternUtils.isRawJpegPair("IMG_001.CR2", "IMG_002.JPG"));
    }

    @Test
    void testIsRawJpegPair_BothRaw() {
        assertFalse(DuplicatePatternUtils.isRawJpegPair("IMG_001.CR2", "IMG_001.NEF"));
    }

    @Test
    void testIsRawJpegPair_BothJpeg() {
        assertFalse(DuplicatePatternUtils.isRawJpegPair("IMG_001.JPG", "IMG_001.JPEG"));
    }

    @Test
    void testIsRawJpegPair_NullInputs() {
        assertFalse(DuplicatePatternUtils.isRawJpegPair(null, "IMG_001.JPG"));
        assertFalse(DuplicatePatternUtils.isRawJpegPair("IMG_001.CR2", null));
    }

    // ===== OS Duplicate Pattern Tests =====

    @Test
    void testHasOSDuplicatePattern_CopyPatterns() {
        assertTrue(DuplicatePatternUtils.hasOSDuplicatePattern("Photo - Copy.jpg"));
        assertTrue(DuplicatePatternUtils.hasOSDuplicatePattern("Photo - Copy (2).jpg"));
        assertTrue(DuplicatePatternUtils.hasOSDuplicatePattern("Photo copy 1.jpg"));
    }

    @Test
    void testHasOSDuplicatePattern_NumberedSuffixes() {
        assertTrue(DuplicatePatternUtils.hasOSDuplicatePattern("Photo (1).jpg"));
        assertTrue(DuplicatePatternUtils.hasOSDuplicatePattern("Photo_1.jpg"));
    }

    @Test
    void testHasOSDuplicatePattern_NormalFiles() {
        assertFalse(DuplicatePatternUtils.hasOSDuplicatePattern("IMG_001.jpg"));
        assertFalse(DuplicatePatternUtils.hasOSDuplicatePattern("Photo.jpg"));
    }

    @Test
    void testHasOSDuplicatePattern_NullInput() {
        assertFalse(DuplicatePatternUtils.hasOSDuplicatePattern(null));
    }

    // ===== Remove Numbered Suffix Tests =====

    @Test
    void testRemoveNumberedSuffix_CopyPatterns() {
        assertEquals("ADLZ2152.JPG", DuplicatePatternUtils.removeNumberedSuffix("ADLZ2152 - Copy.JPG"));
        assertEquals("AFBO7949.JPG", DuplicatePatternUtils.removeNumberedSuffix("AFBO7949 - Copy (2).JPG"));
        assertEquals("Photo.jpg", DuplicatePatternUtils.removeNumberedSuffix("Photo copy 1.jpg"));
    }

    @Test
    void testRemoveNumberedSuffix_NumberedSuffix() {
        assertEquals("Photo.jpg", DuplicatePatternUtils.removeNumberedSuffix("Photo (1).jpg"));
        assertEquals("IMG_001.jpg", DuplicatePatternUtils.removeNumberedSuffix("IMG_001_1.jpg"));
    }

    @Test
    void testRemoveNumberedSuffix_NoPattern() {
        assertEquals("IMG_001.JPG", DuplicatePatternUtils.removeNumberedSuffix("IMG_001.JPG"));
        assertEquals("Photo.jpg", DuplicatePatternUtils.removeNumberedSuffix("Photo.jpg"));
    }

    @Test
    void testRemoveNumberedSuffix_PreserveImportantNumbers() {
        // Should NOT remove numbers that are part of the actual filename
        assertEquals("IMG_9515.JPG", DuplicatePatternUtils.removeNumberedSuffix("IMG_9515.JPG"));
        assertEquals("DSC03215.JPG", DuplicatePatternUtils.removeNumberedSuffix("DSC03215.JPG"));
    }

    @Test
    void testRemoveNumberedSuffix_NullInput() {
        assertNull(DuplicatePatternUtils.removeNumberedSuffix(null));
    }
}
