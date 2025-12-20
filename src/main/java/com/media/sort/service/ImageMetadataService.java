package com.media.sort.service;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.Tag;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
import com.media.sort.model.ExifData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Service for extracting metadata from image files.
 * Handles EXIF, GPS, QuickTime (HEIC), XMP, and PNG metadata.
 */
@Service
public class ImageMetadataService {

    private static final Logger logger = LoggerFactory.getLogger(ImageMetadataService.class);
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

    @Autowired
    private ProgressTrackerFactory progressTrackerFactory;

    private ProgressTracker imageErrorTracker;

    private void initializeTracker() {
        if (progressTrackerFactory != null && imageErrorTracker == null) {
            this.imageErrorTracker = progressTrackerFactory.getImageErrorTracker();
        }
    }

    /**
     * Extract metadata from an image file
     */
    public void processImageFile(ExifData exifData) throws IOException, ImageProcessingException {
        initializeTracker();

        File file = exifData.getFile();
        logger.debug("Processing image file: {} ({})", file.getName(), exifData.getExtension());

        Metadata metadata = ImageMetadataReader.readMetadata(file);

        BasicFileAttributes attr = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
        exifData.setDateCreated(new Date(attr.creationTime().toMillis()));
        exifData.setDateModified(new Date(attr.lastModifiedTime().toMillis()));

        logger.debug("  Initial dates - Created: {}, Modified: {}",
                exifData.getDateCreated(), exifData.getDateModified());

        // Track additional date candidates for validation
        Date exifDateOriginal = null;
        Date exifDateDigitized = null;
        Date gpsDate = null;
        Date quickTimeDate = null;
        Date xmpDate = null;

        // Extract metadata from all available directories
        for (Directory directory : metadata.getDirectories()) {
            if (directory instanceof ExifSubIFDDirectory) {
                exifDateOriginal = processExifDirectory((ExifSubIFDDirectory) directory, exifData);
            } else if (directory instanceof GpsDirectory && !directory.getTags().isEmpty()) {
                gpsDate = processGpsDirectory((GpsDirectory) directory, exifData);
            } else if (directory.getClass().getSimpleName().contains("QuickTime")) {
                // Handle QuickTime metadata (HEIC, MOV, MP4 embedded in images)
                quickTimeDate = processQuickTimeDirectory(directory, exifData);
            } else if (directory.getClass().getSimpleName().contains("Xmp")) {
                // Handle XMP metadata (PNG, WebP, edited images)
                xmpDate = processXmpDirectory(directory, exifData);
            } else if (directory.getClass().getSimpleName().contains("Png")) {
                // Handle PNG-specific metadata
                processPngDirectory(directory, exifData);
            }

            if (exifData.getDeviceName() != null && exifData.getDeviceModel() != null
                    && exifData.getDateTaken() != null
                    && exifData.getGpsLatitude() != null && exifData.getGpsLongitude() != null) {
                break;
            }
        }

        // Apply date priority waterfall if dateTaken is still null
        if (exifData.getDateTaken() == null) {
            selectBestDate(exifData, exifDateOriginal, exifDateDigitized, gpsDate, quickTimeDate, xmpDate);
        }

        // Log extraction results
        if (exifData.getDateTaken() != null) {
            logger.info(" Extracted DateTaken for {}: {}", file.getName(), exifData.getDateTaken());
        } else {
            logger.warn(" No DateTaken found for image: {} - will use filesystem dates", file.getName());
            imageErrorTracker.saveProgress("No Date processImageFile file: " + file);
        }

        // Log final date situation
        Date earliestDate = exifData.getEarliestDate();
        if (earliestDate != null) {
            logger.debug("  Final earliest date for {}: {} (source: {})",
                    file.getName(),
                    earliestDate,
                    earliestDate.equals(exifData.getDateTaken()) ? "EXIF" : "filesystem");
        } else {
            logger.error("  CRITICAL: No date available for image: {}", file.getName());
        }
    }

    /**
     * Select the best date from multiple sources using priority waterfall
     * Priority: GPS > EXIF Original > EXIF Digitized > QuickTime > XMP > Filesystem
     */
    private void selectBestDate(ExifData exifData, Date exifOriginal, Date exifDigitized, Date gps,
            Date quickTime, Date xmp) {
        // Priority 1: GPS date (most reliable - from satellites, UTC)
        if (gps != null && isValidDate(gps)) {
            exifData.setDateTaken(gps);
            logger.debug("Using GPS date: {}", gps);
            return;
        }

        // Priority 2: EXIF DateTimeOriginal
        if (exifOriginal != null && isValidDate(exifOriginal)) {
            exifData.setDateTaken(exifOriginal);
            logger.debug("Using EXIF DateTimeOriginal: {}", exifOriginal);
            return;
        }

        // Priority 3: EXIF DateTimeDigitized
        if (exifDigitized != null && isValidDate(exifDigitized)) {
            exifData.setDateTaken(exifDigitized);
            logger.debug("Using EXIF DateTimeDigitized: {}", exifDigitized);
            return;
        }

        // Priority 4: QuickTime creation time (for HEIC)
        if (quickTime != null && isValidDate(quickTime)) {
            exifData.setDateTaken(quickTime);
            logger.debug("Using QuickTime creation date: {}", quickTime);
            return;
        }

        // Priority 5: XMP metadata (for PNG, WebP, edited images)
        if (xmp != null && isValidDate(xmp)) {
            exifData.setDateTaken(xmp);
            logger.debug("Using XMP date: {}", xmp);
            return;
        }

        // No reliable date found - dateTaken remains null
        logger.debug("No reliable EXIF/metadata date found for: {}", exifData.getFile().getName());
    }

    /**
     * Validate that a date is reasonable (not corrupted)
     */
    private boolean isValidDate(Date date) {
        if (date == null)
            return false;

        try {
            // Reject dates before 2000 (likely corrupted - 1970, 1980 defaults)
            Date minDate = DATE_FORMAT.parse("2000-01-01");
            // Reject dates in the future (camera clock wrong)
            Date maxDate = new Date(System.currentTimeMillis() + (365L * 24 * 60 * 60 * 1000)); // +1 year

            return !date.before(minDate) && !date.after(maxDate);
        } catch (ParseException e) {
            return false;
        }
    }

    /**
     * Process EXIF directory and extract dates + device info
     * Returns DateTimeOriginal for priority comparison
     */
    private Date processExifDirectory(ExifSubIFDDirectory directory, ExifData exifData) {
        Date dateOriginal = null;
        Date dateDigitized = null;

        // Extract multiple EXIF date fields
        if (exifData.getDateTaken() == null) {
            // Try DateTimeOriginal first (most reliable)
            dateOriginal = directory.getDateOriginal();
            if (dateOriginal != null && isValidDate(dateOriginal)) {
                exifData.setDateTaken(dateOriginal);
            }

            // Try DateTimeDigitized as backup
            if (exifData.getDateTaken() == null) {
                dateDigitized = directory.getDateDigitized();
                if (dateDigitized != null && isValidDate(dateDigitized)) {
                    exifData.setDateTaken(dateDigitized);
                }
            }

            // Try generic DateTime as last resort
            if (exifData.getDateTaken() == null) {
                try {
                    Date genericDate = directory.getDate(
                            com.drew.metadata.exif.ExifDirectoryBase.TAG_DATETIME);
                    if (genericDate != null && isValidDate(genericDate)) {
                        exifData.setDateTaken(genericDate);
                    }
                } catch (Exception e) {
                    logger.debug("Could not extract generic DateTime from EXIF");
                }
            }
        }

        // Extract device information
        for (Tag tag : directory.getParent().getTags()) {
            if ("Make".equals(tag.getTagName()) && exifData.getDeviceName() == null) {
                String deviceName = tag.getDescription();
                if (deviceName != null) {
                    exifData.setDeviceName(deviceName.trim());
                }
            }

            if ("Model".equals(tag.getTagName()) && exifData.getDeviceModel() == null) {
                String deviceModel = tag.getDescription();
                if (deviceModel != null) {
                    exifData.setDeviceModel(deviceModel.trim());
                }
            }

            if (exifData.getDeviceName() != null && exifData.getDeviceModel() != null) {
                break;
            }
        }

        return dateOriginal;
    }

    /**
     * Process GPS directory and extract location + GPS timestamp
     */
    private Date processGpsDirectory(GpsDirectory directory, ExifData exifData) {
        com.drew.lang.GeoLocation geoLocation = directory.getGeoLocation();
        if (geoLocation != null) {
            exifData.setLatitude(geoLocation.getLatitude());
            exifData.setLongitude(geoLocation.getLongitude());
        }

        // Extract GPS timestamp (UTC time from satellites - highly reliable)
        Date gpsDate = directory.getGpsDate();
        if (gpsDate != null && isValidDate(gpsDate)) {
            logger.debug("Found GPS date: {}", gpsDate);
            return gpsDate;
        }

        return null;
    }

    /**
     * Process QuickTime directory (for HEIC, MOV, MP4)
     */
    private Date processQuickTimeDirectory(Directory directory, ExifData exifData) {
        try {
            // QuickTime creation time tag
            Date creationTime = directory.getDate(
                    com.drew.metadata.mov.QuickTimeDirectory.TAG_CREATION_TIME);
            if (creationTime != null && isValidDate(creationTime)) {
                logger.debug("Found QuickTime creation time: {}", creationTime);
                return creationTime;
            }
        } catch (Exception e) {
            logger.debug("Could not extract QuickTime date: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Process XMP directory (for PNG, WebP, Adobe-edited images)
     */
    private Date processXmpDirectory(Directory directory, ExifData exifData) {
        try {
            // Try various XMP date tags
            for (Tag tag : directory.getTags()) {
                String tagName = tag.getTagName();
                if (tagName != null && (tagName.contains("Date") || tagName.contains("DateTime"))) {
                    String dateStr = tag.getDescription();
                    if (dateStr != null) {
                        Date xmpDate = parseXmpDate(dateStr);
                        if (xmpDate != null && isValidDate(xmpDate)) {
                            logger.debug("Found XMP date from {}: {}", tagName, xmpDate);
                            return xmpDate;
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("Could not extract XMP date: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Process PNG directory (for PNG-specific metadata)
     */
    private void processPngDirectory(Directory directory, ExifData exifData) {
        try {
            // PNG metadata can be in textual chunks
            for (Tag tag : directory.getTags()) {
                String tagName = tag.getTagName();
                if (tagName != null && tagName.toLowerCase().contains("creation")) {
                    String dateStr = tag.getDescription();
                    if (dateStr != null) {
                        Date pngDate = parseFlexibleDate(dateStr);
                        if (pngDate != null && isValidDate(pngDate) && exifData.getDateTaken() == null) {
                            exifData.setDateTaken(pngDate);
                            logger.debug("Found PNG creation date: {}", pngDate);
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("Could not extract PNG date: {}", e.getMessage());
        }
    }

    /**
     * Parse XMP date string (can be in various formats)
     */
    private Date parseXmpDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty())
            return null;

        // Common XMP date formats
        String[] formats = {
                "yyyy-MM-dd'T'HH:mm:ss",
                "yyyy-MM-dd'T'HH:mm:ssZ",
                "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
                "yyyy-MM-dd HH:mm:ss",
                "yyyy:MM:dd HH:mm:ss"
        };

        for (String format : formats) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(format);
                return sdf.parse(dateStr);
            } catch (ParseException e) {
                // Try next format
            }
        }

        logger.debug("Could not parse XMP date: {}", dateStr);
        return null;
    }

    /**
     * Parse date with flexible format detection
     */
    private Date parseFlexibleDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty())
            return null;

        String[] formats = {
                "yyyy-MM-dd HH:mm:ss",
                "yyyy:MM:dd HH:mm:ss",
                "yyyy-MM-dd",
                "dd/MM/yyyy",
                "MM/dd/yyyy"
        };

        for (String format : formats) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(format);
                return sdf.parse(dateStr);
            } catch (ParseException e) {
                // Try next format
            }
        }

        return null;
    }
}
