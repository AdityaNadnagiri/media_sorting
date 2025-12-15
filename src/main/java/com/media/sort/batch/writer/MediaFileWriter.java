package com.media.sort.batch.writer;

import com.media.sort.MediaSortingProperties;
import com.media.sort.batch.dto.MediaFileDTO;
import com.media.sort.model.ExifData;
import com.media.sort.service.MediaFileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Map;

/**
 * ItemWriter for media files.
 * Moves files to organized directory structure and handles duplicates.
 */
public class MediaFileWriter implements ItemWriter<MediaFileDTO> {

    private static final Logger logger = LoggerFactory.getLogger(MediaFileWriter.class);

    private final MediaFileService mediaFileService;
    private final MediaSortingProperties properties;
    private final String sourceFolder;
    private final Map<String, ExifData> fileHashMap;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    private final com.media.sort.service.PerceptualHashService perceptualHashService;

    private File duplicateImageDirectory;
    private File originalImageDirectory;
    private File duplicateVideoDirectory;
    private File originalVideoDirectory;

    public MediaFileWriter(MediaFileService mediaFileService,
            MediaSortingProperties properties,
            String sourceFolder,
            Map<String, ExifData> fileHashMap,
            com.media.sort.service.PerceptualHashService perceptualHashService) {
        this.mediaFileService = mediaFileService;
        this.properties = properties;
        this.sourceFolder = sourceFolder;
        this.fileHashMap = fileHashMap;
        this.perceptualHashService = perceptualHashService;
        initializeDirectories();
    }

    private void initializeDirectories() {
        this.duplicateImageDirectory = new File(sourceFolder,
                properties.getDirectoryStructure().getImagesDirectoryName() + "/" +
                        properties.getDirectoryStructure().getDuplicateSubDirectoryName());
        this.originalImageDirectory = new File(sourceFolder,
                properties.getDirectoryStructure().getImagesDirectoryName() + "/" +
                        properties.getDirectoryStructure().getOriginalSubDirectoryName());
        this.duplicateVideoDirectory = new File(sourceFolder,
                properties.getDirectoryStructure().getVideosDirectoryName() + "/" +
                        properties.getDirectoryStructure().getDuplicateSubDirectoryName());
        this.originalVideoDirectory = new File(sourceFolder,
                properties.getDirectoryStructure().getVideosDirectoryName() + "/" +
                        properties.getDirectoryStructure().getOriginalSubDirectoryName());
    }

    @Override
    public void write(Chunk<? extends MediaFileDTO> chunk) throws Exception {
        for (MediaFileDTO dto : chunk) {
            moveMediaFile(dto);
        }
    }

    private void moveMediaFile(MediaFileDTO dto) {
        String fileHash = dto.getFileHash();
        ExifData fileData = dto.getExifData();
        boolean isImage = dto.getMediaType() == MediaFileDTO.MediaType.IMAGE;
        String folderDate = fileData.getFolderDate();

        // Fallback to current date if no metadata date is available
        if (folderDate == null || folderDate.isEmpty()) {
            folderDate = dateFormat.format(new java.util.Date());
            logger.warn("No date metadata found for file: {}, using current date: {}",
                    fileData.getFile().getName(), folderDate);
        }

        ExifData originalFileData = fileHashMap.get(fileHash);

        // Critical Check: Does the original file ACTUALLY exist?
        // If it's in the map but missing from disk, we must treat the current file as
        // the new original.
        if (originalFileData != null && !originalFileData.getFile().exists()) {
            logger.warn(
                    "Original file registered in map but missing from disk: {}. Treating current file as new Original.",
                    originalFileData.getFile().getAbsolutePath());
            originalFileData = null; // Reset so we enter the "First occurrence" block
            fileHashMap.remove(fileHash); // Remove stale entry
        }

        if (originalFileData != null) {
            // Exact duplicate found (same SHA-256 hash) AND original exists on disk
            folderDate = getNewFolderDateForDuplicates(fileData, originalFileData);

            // Use isBetterQualityThan instead of isAfter to consider OS duplicate patterns
            boolean currentIsBetter = fileData.isBetterQualityThan(originalFileData);

            if (isImage) {
                if (!currentIsBetter) {
                    // Current file is WORSE quality (or has copy pattern) - it's a duplicate
                    mediaFileService.executeMove(fileData, new File(duplicateImageDirectory, folderDate), true, false);
                    logger.info("Moved duplicate: {} to Duplicates, kept better original: {}",
                            fileData.getFile().getName(), originalFileData.getFile().getName());
                } else {
                    // Current file is BETTER quality (or existing has copy pattern) - it should be
                    // the original
                    // 1. Move the existing (worse quality) file to duplicates
                    mediaFileService.executeMove(originalFileData, new File(duplicateImageDirectory, folderDate), true,
                            false);

                    // 2. Move the current (better quality) file to originals (clean name)
                    mediaFileService.executeMove(fileData, new File(originalImageDirectory, folderDate), false, true);

                    // 3. Update map ONLY after successful moves
                    if (fileData.getFile().exists()) { // Verify move succeeded
                        fileHashMap.put(fileHash, fileData);
                    }

                    logger.info("Moved previous original: {} to Duplicates, kept better original: {}",
                            originalFileData.getFile().getName(), fileData.getFile().getName());
                }
            } else {
                if (!currentIsBetter) {
                    // Current file is WORSE quality (or has copy pattern) - it's a duplicate
                    mediaFileService.executeMove(fileData, new File(duplicateVideoDirectory, folderDate), true, false);
                    logger.info("Moved duplicate: {} to Duplicates, kept better original: {}",
                            fileData.getFile().getName(), originalFileData.getFile().getName());
                } else {
                    // Current file is BETTER quality (or existing has copy pattern) - it should be
                    // the original
                    // 1. Move the existing (worse quality) file to duplicates
                    mediaFileService.executeMove(originalFileData, new File(duplicateVideoDirectory, folderDate), true,
                            false);

                    // 2. Move the current (better quality) file to originals (clean name)
                    mediaFileService.executeMove(fileData, new File(originalVideoDirectory, folderDate), false, true);

                    // 3. Update map ONLY after successful moves
                    if (fileData.getFile().exists()) {
                        fileHashMap.put(fileHash, fileData);
                    }

                    logger.info("Moved previous original: {} to Duplicates, kept better original: {}",
                            originalFileData.getFile().getName(), fileData.getFile().getName());
                }
            }
        } else {
            // No exact content match - check for perceptual duplicates (images only)
            if (isImage && fileData.getPerceptualHash() != null) {
                ExifData perceptualDuplicate = findPerceptualDuplicate(fileData);

                if (perceptualDuplicate != null) {
                    // Found a visually similar image!
                    logger.info("Perceptual duplicate detected: {} similar to {}",
                            fileData.getFile().getName(), perceptualDuplicate.getFile().getName());

                    // Compare quality scores to determine which is better
                    if (fileData.isBetterQualityThan(perceptualDuplicate)) {
                        // Current file is BETTER quality - it should be the original
                        logger.info("Current file {} has better quality ({}px) than existing {} ({}px)",
                                fileData.getFile().getName(), fileData.getQualityScore(),
                                perceptualDuplicate.getFile().getName(), perceptualDuplicate.getQualityScore());

                        // Move lower quality to Duplicate
                        mediaFileService.executeMove(perceptualDuplicate,
                                new File(duplicateImageDirectory, folderDate), true, false);

                        // Move current (better quality) to Original (clean name)
                        mediaFileService.executeMove(fileData,
                                new File(originalImageDirectory, folderDate), false, true);

                        // Update map: Remove old hash and add new hash
                        // Find and remove the old file's hash from the map
                        String oldHash = null;
                        for (Map.Entry<String, ExifData> entry : fileHashMap.entrySet()) {
                            if (entry.getValue() == perceptualDuplicate) {
                                oldHash = entry.getKey();
                                break;
                            }
                        }
                        if (oldHash != null) {
                            fileHashMap.remove(oldHash);
                            logger.info("Removed old hash {} for lower quality file from map", oldHash);
                        }

                        // Add new hash for better quality file
                        if (fileData.getFile().exists()) {
                            fileHashMap.put(fileHash, fileData);
                            logger.info("Added new hash {} for better quality file to map", fileHash);
                        }
                    } else {
                        // Existing file is BETTER quality - keep it as original
                        logger.info(
                                "Existing file {} has better quality ({}px), moving current {} ({}px) to Duplicates",
                                perceptualDuplicate.getFile().getName(), perceptualDuplicate.getQualityScore(),
                                fileData.getFile().getName(), fileData.getQualityScore());

                        // Move current (lower quality) to Duplicate
                        mediaFileService.executeMove(fileData,
                                new File(duplicateImageDirectory, folderDate), true, false);
                    }
                    return; // Done processing this perceptual duplicate
                }
            }

            // First occurrence - original file (unique, no duplicate - clean name)
            if (isImage) {
                mediaFileService.executeMove(fileData, new File(originalImageDirectory, folderDate), false, true);
            } else {
                mediaFileService.executeMove(fileData, new File(originalVideoDirectory, folderDate), false, true);
            }

            // Only add to map if move succeeded
            if (fileData.getFile().exists()) {
                fileHashMap.put(fileHash, fileData);
                logger.info("Moved original file: {} to {}", fileData.getFile().getName(), folderDate);
            } else {
                logger.error("Failed to move original file, not adding to map: {}", fileData.getFile().getName());
            }
        }
    }

    private String getNewFolderDateForDuplicates(ExifData fileData, ExifData existingFileData) {
        String currentDate = dateFormat.format(new java.util.Date());

        // Get folder dates with fallback to current date
        String fileDate = fileData.getFolderDate();
        String existingDate = existingFileData.getFolderDate();

        if (fileDate == null || fileDate.isEmpty()) {
            fileDate = currentDate;
        }
        if (existingDate == null || existingDate.isEmpty()) {
            existingDate = currentDate;
        }

        try {
            if (dateFormat.parse(fileDate).after(dateFormat.parse(existingDate))) {
                return existingDate;
            }
        } catch (ParseException e) {
            logger.error("Failed to parse folder dates for duplicates comparison: {} vs {}", fileDate, existingDate, e);
        }
        return fileDate;
    }

    /**
     * Search for a perceptual duplicate of the given image
     * 
     * @param fileData The image to check
     * @return Perceptually similar ExifData from the map, or null if none found
     */
    private ExifData findPerceptualDuplicate(ExifData fileData) {
        if (fileData.getPerceptualHash() == null) {
            return null;
        }

        // Search through all processed files for perceptually similar images
        for (ExifData existing : fileHashMap.values()) {
            // Only compare against images
            if (existing.isImage() && existing.getPerceptualHash() != null) {
                // Check if hashes are perceptually similar
                if (perceptualHashService.areSimilar(fileData.getPerceptualHash(),
                        existing.getPerceptualHash())) {
                    return existing;
                }
            }
        }

        return null;
    }
}
