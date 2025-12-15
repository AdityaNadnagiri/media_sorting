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

    private File duplicateImageDirectory;
    private File originalImageDirectory;
    private File duplicateVideoDirectory;
    private File originalVideoDirectory;

    public MediaFileWriter(MediaFileService mediaFileService,
            MediaSortingProperties properties,
            String sourceFolder,
            Map<String, ExifData> fileHashMap) {
        this.mediaFileService = mediaFileService;
        this.properties = properties;
        this.sourceFolder = sourceFolder;
        this.fileHashMap = fileHashMap;
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
            // Duplicate file found AND original exists on disk
            folderDate = getNewFolderDateForDuplicates(fileData, originalFileData);
            boolean isAfter = fileData.isAfter(originalFileData);

            if (isImage) {
                if (isAfter) {
                    // Current file is NEWER - it's a duplicate, keep older one as original
                    mediaFileService.executeMove(fileData, new File(duplicateImageDirectory, folderDate), true, false);
                    logger.info("Moved newer duplicate: {} to Duplicates, kept older original: {}",
                            fileData.getFile().getName(), originalFileData.getFile().getName());
                } else {
                    // Current file is OLDER - it's the true original, move newer one to duplicates
                    // 1. Move the existing (newer) original to duplicates
                    mediaFileService.executeMove(originalFileData, new File(duplicateImageDirectory, folderDate), true,
                            false);

                    // 2. Move the current (older) file to originals
                    mediaFileService.executeMove(fileData, new File(originalImageDirectory, folderDate), false, true);

                    // 3. Update map ONLY after successful moves
                    if (fileData.getFile().exists()) { // Verify move succeeded
                        fileHashMap.put(fileHash, fileData);
                    }

                    logger.info("Moved newer duplicate: {} to Duplicates, kept older original: {}",
                            originalFileData.getFile().getName(), fileData.getFile().getName());
                }
            } else {
                if (isAfter) {
                    // Current file is NEWER - it's a duplicate, keep older one as original
                    mediaFileService.executeMove(fileData, new File(duplicateVideoDirectory, folderDate), true, false);
                    logger.info("Moved newer duplicate: {} to Duplicates, kept older original: {}",
                            fileData.getFile().getName(), originalFileData.getFile().getName());
                } else {
                    // Current file is OLDER - it's the true original, move newer one to duplicates
                    // 1. Move the existing (newer) original to duplicates
                    mediaFileService.executeMove(originalFileData, new File(duplicateVideoDirectory, folderDate), true,
                            false);

                    // 2. Move the current (older) file to originals
                    mediaFileService.executeMove(fileData, new File(originalVideoDirectory, folderDate), false, true);

                    // 3. Update map ONLY after successful moves
                    if (fileData.getFile().exists()) {
                        fileHashMap.put(fileHash, fileData);
                    }

                    logger.info("Moved newer duplicate: {} to Duplicates, kept older original: {}",
                            originalFileData.getFile().getName(), fileData.getFile().getName());
                }
            }
        } else {
            // First occurrence - original file (unique, no duplicate)
            if (isImage) {
                mediaFileService.executeMove(fileData, new File(originalImageDirectory, folderDate), false, false);
            } else {
                mediaFileService.executeMove(fileData, new File(originalVideoDirectory, folderDate), false, false);
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
}
