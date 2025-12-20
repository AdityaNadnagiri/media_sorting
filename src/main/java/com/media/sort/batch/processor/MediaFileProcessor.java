package com.media.sort.batch.processor;

import com.media.sort.batch.dto.MediaFileDTO;
import com.media.sort.model.ExifData;
import com.media.sort.service.ExifDataFactory;
import com.media.sort.service.MediaFileService;
import com.media.sort.service.PerceptualHashService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemProcessor;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * ItemProcessor for media files.
 * Extracts EXIF data, calculates hash, and determines target location.
 */
@Slf4j
@RequiredArgsConstructor
public class MediaFileProcessor implements ItemProcessor<File, MediaFileDTO> {

    private final MediaFileService mediaFileService;
    private final ExifDataFactory exifDataFactory;
    private final PerceptualHashService perceptualHashService;

    @Override
    public MediaFileDTO process(File file) throws Exception {
        try {
            // Create ExifData object using factory (handles all dependency injection)
            ExifData exifData = exifDataFactory.createExifData(file);

            // Skip "other" files (non-media files)
            if (exifData.isOther()) {
                log.debug("Skipping non-media file: {}", file.getAbsolutePath());
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
                    log.warn("Failed to compute perceptual hash/dimensions for: {}", file.getAbsolutePath(), e);
                }
            }

            // Determine media type
            MediaFileDTO.MediaType mediaType = exifData.isImage()
                    ? MediaFileDTO.MediaType.IMAGE
                    : MediaFileDTO.MediaType.VIDEO;

            // Create DTO using Record constructor
            MediaFileDTO dto = new MediaFileDTO(
                    file,
                    null, // target path determined by writer
                    exifData,
                    fileHash,
                    mediaType);

            log.debug("Processed file: {} (hash: {})", file.getAbsolutePath(), fileHash);
            return dto;

        } catch (Exception e) {
            log.error("Error processing file: {}", file.getAbsolutePath(), e);
            return null;
        }
    }
}
