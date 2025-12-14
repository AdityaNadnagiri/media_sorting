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

        if (fileHashMap.containsKey(fileHash)) {
            // Duplicate file found
            ExifData originalFileData = fileHashMap.get(fileHash);
            folderDate = getNewFolderDateForDuplicates(fileData, originalFileData);
            boolean isAfter = fileData.isAfter(originalFileData);

            if (isImage) {
                if (isAfter) {
                    // Current file is NEWER - it's a duplicate, keep older one as original
                    mediaFileService.executeMove(fileData, new File(duplicateImageDirectory, folderDate), true);
                    logger.info("Moved newer duplicate: {} to Duplicates, kept older original: {}",
                            fileData.getFile().getName(), originalFileData.getFile().getName());
                } else {
                    // Current file is OLDER - it's the true original, move newer one to duplicates
                    mediaFileService.executeMove(originalFileData, new File(duplicateImageDirectory, folderDate), true);
                    mediaFileService.executeMove(fileData, new File(originalImageDirectory, folderDate), false);
                    fileHashMap.put(fileHash, fileData); // Update map with older (true original) file
                    logger.info("Moved newer duplicate: {} to Duplicates, kept older original: {}",
                            originalFileData.getFile().getName(), fileData.getFile().getName());
                }
            } else {
                if (isAfter) {
                    // Current file is NEWER - it's a duplicate, keep older one as original
                    mediaFileService.executeMove(fileData, new File(duplicateVideoDirectory, folderDate), true);
                    logger.info("Moved newer duplicate: {} to Duplicates, kept older original: {}",
                            fileData.getFile().getName(), originalFileData.getFile().getName());
                } else {
                    // Current file is OLDER - it's the true original, move newer one to duplicates
                    mediaFileService.executeMove(originalFileData, new File(duplicateVideoDirectory, folderDate), true);
                    mediaFileService.executeMove(fileData, new File(originalVideoDirectory, folderDate), false);
                    fileHashMap.put(fileHash, fileData); // Update map with older (true original) file
                    logger.info("Moved newer duplicate: {} to Duplicates, kept older original: {}",
                            originalFileData.getFile().getName(), fileData.getFile().getName());
                }
            }
        } else {
            // First occurrence - original file
            if (isImage) {
                mediaFileService.executeMove(fileData, new File(originalImageDirectory, folderDate), false);
            } else {
                mediaFileService.executeMove(fileData, new File(originalVideoDirectory, folderDate), false);
            }
            fileHashMap.put(fileHash, fileData);
            logger.info("Moved original file: {} to {}", fileData.getFile().getName(), folderDate);
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
