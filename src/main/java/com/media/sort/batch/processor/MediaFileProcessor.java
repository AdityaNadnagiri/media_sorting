package com.media.sort.batch.processor;

import com.media.sort.batch.dto.MediaFileDTO;
import com.media.sort.model.ExifData;

import com.media.sort.service.MediaFileService;
import com.media.sort.service.PerceptualHashService;
import com.media.sort.service.ProgressTrackerFactory;
import com.media.sort.service.VideoExifDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemProcessor;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * ItemProcessor for media files.
 * Extracts EXIF data, calculates hash, and determines target location.
 */
public class MediaFileProcessor implements ItemProcessor<File, MediaFileDTO> {

    private static final Logger logger = LoggerFactory.getLogger(MediaFileProcessor.class);

    private final MediaFileService mediaFileService;
    private final ProgressTrackerFactory progressTrackerFactory;
    private final VideoExifDataService videoExifDataService;
    private final PerceptualHashService perceptualHashService;

    public MediaFileProcessor(MediaFileService mediaFileService,
            ProgressTrackerFactory progressTrackerFactory,
            VideoExifDataService videoExifDataService,
            PerceptualHashService perceptualHashService) {
        this.mediaFileService = mediaFileService;
        this.progressTrackerFactory = progressTrackerFactory;
        this.videoExifDataService = videoExifDataService;
        this.perceptualHashService = perceptualHashService;
    }

    @Override
    public MediaFileDTO process(File file) throws Exception {
        try {
            // Create ExifData object
            ExifData exifData = new ExifData(file);

            // Set dependencies
            exifData.setVideoExifDataService(videoExifDataService);

            // Initialize progress trackers
            if (progressTrackerFactory != null) {
                exifData.setProgressTrackers(
                        progressTrackerFactory.getImageErrorTracker(),
                        progressTrackerFactory.getFileComparisonTracker(),
                        progressTrackerFactory.getFileComparisonTracker());
            }

            // Skip "other" files (non-media files)
            if (exifData.isOther()) {
                logger.debug("Skipping non-media file: {}", file.getAbsolutePath());
                return null;
            }

            // Calculate file hash for duplicate detection
            String fileHash = mediaFileService.calculateHash(file.toPath());

            // For images: Compute perceptual hash and extract dimensions
            if (exifData.isImage()) {
                try {
                    // Compute perceptual hash
                    String pHash = perceptualHashService.computeHash(file);
                    exifData.setPerceptualHash(pHash);

                    // Extract image dimensions
                    BufferedImage img = ImageIO.read(file);
                    if (img != null) {
                        exifData.setImageWidth(img.getWidth());
                        exifData.setImageHeight(img.getHeight());
                    }
                } catch (Exception e) {
                    logger.warn("Failed to compute perceptual hash/dimensions for: {}", file.getName(), e);
                }
            }

            // Determine media type
            MediaFileDTO.MediaType mediaType = exifData.isImage() ? MediaFileDTO.MediaType.IMAGE
                    : MediaFileDTO.MediaType.VIDEO;

            // Create DTO (target path will be determined by writer based on duplicate
            // status)
            MediaFileDTO dto = new MediaFileDTO();
            dto.setSourceFile(file);
            dto.setExifData(exifData);
            dto.setFileHash(fileHash);
            dto.setMediaType(mediaType);

            logger.debug("Processed file: {} (hash: {})", file.getName(), fileHash);
            return dto;

        } catch (Exception e) {
            logger.error("Error processing file: {}", file.getAbsolutePath(), e);
            // Return null to skip this file
            return null;
        }
    }
}
