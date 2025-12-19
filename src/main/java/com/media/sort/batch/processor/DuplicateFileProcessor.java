package com.media.sort.batch.processor;

import com.media.sort.batch.dto.FileMoveDTO;
import com.media.sort.batch.dto.FileHashDTO;
import com.media.sort.model.ExifData;
import com.media.sort.service.FileQualityComparator;
import com.media.sort.service.MediaFileService;
import com.media.sort.service.PerceptualHashService;
import com.media.sort.service.ProgressTrackerFactory;
import com.media.sort.util.DuplicatePatternUtils;
import com.media.sort.util.FileOperationUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemProcessor;

import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enhanced ItemProcessor that checks if a file is a duplicate and determines
 * quality.
 * Includes burst detection, RAW+JPEG pairing, and perceptual hashing.
 * Compares file size, EXIF data, and dates to determine which is original vs
 * duplicate.
 */
@Slf4j
@RequiredArgsConstructor
public class DuplicateFileProcessor implements ItemProcessor<File, FileMoveDTO> {

    private final MediaFileService mediaFileService;
    private final FileQualityComparator qualityComparator;
    private final ProgressTrackerFactory progressTrackerFactory;
    private final ConcurrentHashMap<String, FileHashDTO> referenceHashMap;
    private final PerceptualHashService perceptualHashService;

    @Override
    public FileMoveDTO process(File file) throws Exception {
        try {
            String hash = mediaFileService.calculateHash(file.toPath());

            // Check for exact hash match
            if (referenceHashMap.containsKey(hash)) {
                FileHashDTO referenceDTO = referenceHashMap.get(hash);

                // Apply burst detection - skip if files are sequential burst shots
                if (isBurstSequence(file, referenceDTO.getFile())) {
                    log.info("Burst sequence detected, skipping duplicate marking: {} and {}",
                            file.getName(), referenceDTO.getFile().getName());
                    return null;
                }

                // Apply RAW+JPEG pairing - skip if files are RAW+JPEG pair
                if (isRawJpegPair(file, referenceDTO.getFile())) {
                    log.info("RAW+JPEG pair detected, skipping duplicate marking: {} and {}",
                            file.getName(), referenceDTO.getFile().getName());
                    return null;
                }

                log.info("Exact duplicate found: {} matches {}",
                        file.getAbsolutePath(), referenceDTO.getFilePath().toString());

                return processDuplicate(file, referenceDTO);
            }

            // Check for perceptual duplicate (visually similar)
            if (perceptualHashService != null && isImageFile(file)) {
                FileHashDTO perceptualMatch = findPerceptualDuplicate(file);
                if (perceptualMatch != null) {
                    // Apply same filters
                    if (isBurstSequence(file, perceptualMatch.getFile())) {
                        log.info("Burst sequence detected (perceptual), skipping: {} and {}",
                                file.getName(), perceptualMatch.getFile().getName());
                        return null;
                    }

                    if (isRawJpegPair(file, perceptualMatch.getFile())) {
                        log.info("RAW+JPEG pair detected (perceptual), skipping: {} and {}",
                                file.getName(), perceptualMatch.getFile().getName());
                        return null;
                    }

                    log.info("Perceptual duplicate found: {} visually similar to {}",
                            file.getAbsolutePath(), perceptualMatch.getFilePath().toString());

                    return processDuplicate(file, perceptualMatch);
                }
            }

            // Not a duplicate
            return null;

        } catch (Exception e) {
            log.error("Error processing file for duplicates: {}", file.getAbsolutePath(), e);
            return null;
        }
    }

    /**
     * Process a duplicate file and create appropriate move DTO
     */
    private FileMoveDTO processDuplicate(File file, FileHashDTO referenceDTO) {
        // Extract EXIF data for current file if it's a media file
        ExifData sourceExif = extractExifData(file);
        ExifData referenceExif = referenceDTO.getExifData();

        // Compare quality
        FileQualityComparator.ComparisonResult comparison = qualityComparator.compareFiles(
                file,
                referenceDTO.getFile(),
                sourceExif,
                referenceExif);

        // Determine move strategy based on which is original
        return createMoveDTO(file, referenceDTO, comparison);
    }

    /**
     * Check if files are part of a burst sequence
     */
    private boolean isBurstSequence(File file1, File file2) {
        return DuplicatePatternUtils.isBurstSequence(file1.getName(), file2.getName());
    }

    /**
     * Check if files are a RAW+JPEG pair
     */
    private boolean isRawJpegPair(File file1, File file2) {
        return DuplicatePatternUtils.isRawJpegPair(file1.getName(), file2.getName());
    }

    /**
     * Check if file is an image
     */
    private boolean isImageFile(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".jpg") || name.endsWith(".jpeg") ||
                name.endsWith(".png") || name.endsWith(".gif") ||
                name.endsWith(".bmp") || name.endsWith(".tiff") ||
                name.endsWith(".webp") || name.endsWith(".heic");
    }

    /**
     * Find perceptual duplicate by comparing perceptual hashes
     */
    private FileHashDTO findPerceptualDuplicate(File file) {
        try {
            String perceptualHash = perceptualHashService.computeHash(file);
            if (perceptualHash == null) {
                return null;
            }

            // Search through reference hash map for perceptually similar images
            for (FileHashDTO refDTO : referenceHashMap.values()) {
                if (refDTO.getPerceptualHash() != null) {
                    if (perceptualHashService.areSimilar(perceptualHash, refDTO.getPerceptualHash())) {
                        return refDTO;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to find perceptual duplicate for: {}", file.getAbsolutePath(), e);
        }
        return null;
    }

    /**
     * Extract EXIF data from file if it's a media file
     */
    private ExifData extractExifData(File file) {
        try {
            ExifData exifData = new ExifData(file);

            // Set dependencies
            exifData.setVideoExifDataService(null); // Video service not needed for comparison
            exifData.setVideoQualityComparator(null); // Uses FileQualityComparator instead

            if (progressTrackerFactory != null) {
                exifData.setProgressTrackers(
                        progressTrackerFactory.getImageErrorTracker(),
                        progressTrackerFactory.getFileComparisonTracker(),
                        progressTrackerFactory.getFileComparisonTracker());
            }

            // Return null if it's not a media file
            if (exifData.isOther()) {
                return null;
            }

            return exifData;
        } catch (Exception e) {
            log.warn("Failed to extract EXIF data for: {}", file.getAbsolutePath(), e);
        }

        return null;
    }

    /**
     * Create FileMoveDTO based on quality comparison result
     */
    private FileMoveDTO createMoveDTO(File sourceFile, FileHashDTO referenceDTO,
            FileQualityComparator.ComparisonResult comparison) {

        boolean sourceIsOriginal = comparison.isFile1IsOriginal();

        if (sourceIsOriginal) {
            // Source file is higher quality - it should stay, reference should move to
            // duplicates
            // But since we're processing folder1, we need to move source to replace
            // reference
            // and move reference to duplicates
            log.info("Source file {} is higher quality than reference {}",
                    sourceFile.getAbsolutePath(), referenceDTO.getFilePath().toString());

            // Determine target path: replace reference file location
            Path targetPath = referenceDTO.getFilePath();

            // The reference file will be moved to duplicates by the writer
            FileMoveDTO dto = new FileMoveDTO(
                    sourceFile.toPath(),
                    targetPath,
                    referenceDTO.getFilePath(),
                    true, // source is original
                    comparison.getOriginalExif(),
                    comparison.getDuplicateExif());

            return dto;

        } else {
            // Reference file is higher quality - move source to duplicates folder
            log.info("Reference file {} is higher quality than source {}",
                    referenceDTO.getFilePath().toString(), sourceFile.getAbsolutePath());

            // Determine duplicates folder path based on reference location
            Path referencePath = referenceDTO.getFilePath();
            Path referenceParent = referencePath.getParent();

            // Create "Duplicates" subfolder
            Path duplicatesFolder = referenceParent.resolve("Duplicates");
            Path targetPath = duplicatesFolder.resolve(sourceFile.getName());

            // Find unique filename if needed
            if (targetPath.toFile().exists()) {
                targetPath = FileOperationUtils.findUniqueFileName(targetPath);
            }

            FileMoveDTO dto = new FileMoveDTO(
                    sourceFile.toPath(),
                    targetPath,
                    referencePath,
                    false, // reference is original
                    comparison.getDuplicateExif(),
                    comparison.getOriginalExif());

            return dto;
        }
    }
}
