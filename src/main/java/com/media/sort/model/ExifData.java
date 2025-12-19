package com.media.sort.model;

import com.drew.imaging.ImageProcessingException;
import com.drew.imaging.ImageMetadataReader;
import com.drew.lang.GeoLocation;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.Tag;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
import com.media.sort.service.FileTypeRegistry;
import com.media.sort.service.ProgressTracker;
import com.media.sort.service.VideoExifDataService;
import com.media.sort.service.VideoQualityComparator;
import com.media.sort.util.DuplicatePatternUtils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Data model for EXIF metadata extracted from media files.
 * This is NOT a Spring component - instances are created directly.
 * Uses Lombok @Data for standard getters/setters while preserving custom
 * methods.
 */
@Slf4j
@Data
public class ExifData {

    private ProgressTracker imageErrorTracker;
    private ProgressTracker compressionTracker;
    private ProgressTracker fileTracker;

    // Dependencies passed via setter methods
    private VideoExifDataService videoExifDataService;
    private VideoQualityComparator videoQualityComparator;

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

    // File properties
    private File file;
    private String deviceName;
    private String deviceModel;
    private Date dateTaken;
    private Date dateCreated;
    private Date dateModified;
    private Double latitude;
    private Double longitude;
    private String type;
    private String folderDate;
    private String extension;

    // Perceptual duplicate detection
    private String perceptualHash; // pHash for images
    private Integer imageWidth; // For quality comparison
    private Integer imageHeight;
    private Long fileSize; // File size in bytes

    // Default constructor - trackers will be initialized via ProgressTrackerFactory
    public ExifData() {
        // Trackers will be set via setProgressTrackers method when
        // ProgressTrackerFactory is available
        // This avoids hardcoded paths
    }

    public ExifData(File file) {
        this();
        processFile(file);
    }

    /**
     * Initialize progress trackers using ProgressTrackerFactory
     * This method should be called by services that have access to
     * ProgressTrackerFactory
     */
    public void setProgressTrackers(ProgressTracker imageErrorTracker,
            ProgressTracker compressionTracker,
            ProgressTracker fileTracker) {
        this.imageErrorTracker = imageErrorTracker;
        this.compressionTracker = compressionTracker;
        this.fileTracker = fileTracker;
    }

    /**
     * Set VideoExifDataService dependency
     */
    public void setVideoExifDataService(VideoExifDataService videoExifDataService) {
        this.videoExifDataService = videoExifDataService;
    }

    /**
     * Set VideoQualityComparator dependency for video-specific quality comparison
     */
    public void setVideoQualityComparator(VideoQualityComparator videoQualityComparator) {
        this.videoQualityComparator = videoQualityComparator;
    }

    public void processFile(File file) {
        try {
            this.file = file;
            this.fileSize = file.length(); // Capture file size for quality comparison
            setImageExifDataType();

            if (isImage()) {
                processImageFile();
            } else if (isVideo() && videoExifDataService != null) {
                videoExifDataService.processVideoFile(this);
            }

            reorderDates();
            determineFolderDate();

        } catch (IOException | ImageProcessingException e) {
            log.error("Failed to process file: {}", file.getAbsolutePath(), e);
            this.type = "other";
            imageErrorTracker.saveProgress("ExifData file: " + file);
        }
    }

    private void processImageFile() throws IOException, ImageProcessingException {
        Metadata metadata = ImageMetadataReader.readMetadata(file);

        BasicFileAttributes attr = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
        this.dateCreated = new Date(attr.creationTime().toMillis());
        this.dateModified = new Date(attr.lastModifiedTime().toMillis());

        // Track additional date candidates for validation
        Date exifDateOriginal = null;
        Date exifDateDigitized = null;
        Date gpsDate = null;
        Date quickTimeDate = null;
        Date xmpDate = null;

        // Extract metadata from all available directories
        for (Directory directory : metadata.getDirectories()) {
            if (directory instanceof ExifSubIFDDirectory) {
                exifDateOriginal = processExifDirectory((ExifSubIFDDirectory) directory);
            } else if (directory instanceof GpsDirectory && !directory.getTags().isEmpty()) {
                gpsDate = processGpsDirectory((GpsDirectory) directory);
            } else if (directory.getClass().getSimpleName().contains("QuickTime")) {
                // Handle QuickTime metadata (HEIC, MOV, MP4 embedded in images)
                quickTimeDate = processQuickTimeDirectory(directory);
            } else if (directory.getClass().getSimpleName().contains("Xmp")) {
                // Handle XMP metadata (PNG, WebP, edited images)
                xmpDate = processXmpDirectory(directory);
            } else if (directory.getClass().getSimpleName().contains("Png")) {
                // Handle PNG-specific metadata
                processPngDirectory(directory);
            }

            if (deviceName != null && deviceModel != null && dateTaken != null &&
                    latitude != null && longitude != null) {
                break;
            }
        }

        // Apply date priority waterfall if dateTaken is still null
        if (dateTaken == null) {
            selectBestDate(exifDateOriginal, exifDateDigitized, gpsDate, quickTimeDate, xmpDate);
        }
    }

    /**
     * Select the best date from multiple sources using priority waterfall
     * Priority: GPS > EXIF Original > EXIF Digitized > QuickTime > XMP > Filesystem
     */
    private void selectBestDate(Date exifOriginal, Date exifDigitized, Date gps,
            Date quickTime, Date xmp) {
        // Priority 1: GPS date (most reliable - from satellites, UTC)
        if (gps != null && isValidDate(gps)) {
            dateTaken = gps;
            log.debug("Using GPS date: {}", gps);
            return;
        }

        // Priority 2: EXIF DateTimeOriginal
        if (exifOriginal != null && isValidDate(exifOriginal)) {
            dateTaken = exifOriginal;
            log.debug("Using EXIF DateTimeOriginal: {}", exifOriginal);
            return;
        }

        // Priority 3: EXIF DateTimeDigitized
        if (exifDigitized != null && isValidDate(exifDigitized)) {
            dateTaken = exifDigitized;
            log.debug("Using EXIF DateTimeDigitized: {}", exifDigitized);
            return;
        }

        // Priority 4: QuickTime creation time (for HEIC)
        if (quickTime != null && isValidDate(quickTime)) {
            dateTaken = quickTime;
            log.debug("Using QuickTime creation date: {}", quickTime);
            return;
        }

        // Priority 5: XMP metadata (for PNG, WebP, edited images)
        if (xmp != null && isValidDate(xmp)) {
            dateTaken = xmp;
            log.debug("Using XMP date: {}", xmp);
            return;
        }

        // No reliable date found - dateTaken remains null
        log.debug("No reliable EXIF/metadata date found for: {}", file.getName());
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
    private Date processExifDirectory(ExifSubIFDDirectory directory) {
        Date dateOriginal = null;
        Date dateDigitized = null;

        // Extract multiple EXIF date fields
        if (dateTaken == null) {
            // Try DateTimeOriginal first (most reliable)
            dateOriginal = directory.getDateOriginal();
            if (dateOriginal != null && isValidDate(dateOriginal)) {
                dateTaken = dateOriginal;
            }

            // Try DateTimeDigitized as backup
            if (dateTaken == null) {
                dateDigitized = directory.getDateDigitized();
                if (dateDigitized != null && isValidDate(dateDigitized)) {
                    dateTaken = dateDigitized;
                }
            }

            // Try generic DateTime as last resort
            if (dateTaken == null) {
                try {
                    Date genericDate = directory.getDate(
                            com.drew.metadata.exif.ExifDirectoryBase.TAG_DATETIME);
                    if (genericDate != null && isValidDate(genericDate)) {
                        dateTaken = genericDate;
                    }
                } catch (Exception e) {
                    log.debug("Could not extract generic DateTime from EXIF");
                }
            }
        }

        // Extract device information
        for (Tag tag : directory.getParent().getTags()) {
            if ("Make".equals(tag.getTagName()) && deviceName == null) {
                deviceName = tag.getDescription();
                if (deviceName != null) {
                    deviceName = deviceName.trim();
                }
            }

            if ("Model".equals(tag.getTagName()) && deviceModel == null) {
                deviceModel = tag.getDescription();
                if (deviceModel != null) {
                    deviceModel = deviceModel.trim();
                }
            }

            if (deviceName != null && deviceModel != null) {
                break;
            }
        }

        return dateOriginal;
    }

    /**
     * Process QuickTime directory (for HEIC, MOV, MP4)
     */
    private Date processQuickTimeDirectory(Directory directory) {
        try {
            // QuickTime creation time tag
            Date creationTime = directory.getDate(
                    com.drew.metadata.mov.QuickTimeDirectory.TAG_CREATION_TIME);
            if (creationTime != null && isValidDate(creationTime)) {
                log.debug("Found QuickTime creation time: {}", creationTime);
                return creationTime;
            }
        } catch (Exception e) {
            log.debug("Could not extract QuickTime date: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Process XMP directory (for PNG, WebP, Adobe-edited images)
     */
    private Date processXmpDirectory(Directory directory) {
        try {
            // Try various XMP date tags
            for (Tag tag : directory.getTags()) {
                String tagName = tag.getTagName();
                if (tagName != null && (tagName.contains("Date") || tagName.contains("DateTime"))) {
                    String dateStr = tag.getDescription();
                    if (dateStr != null) {
                        Date xmpDate = parseXmpDate(dateStr);
                        if (xmpDate != null && isValidDate(xmpDate)) {
                            log.debug("Found XMP date from {}: {}", tagName, xmpDate);
                            return xmpDate;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Could not extract XMP date: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Process PNG directory (for PNG-specific metadata)
     */
    private void processPngDirectory(Directory directory) {
        try {
            // PNG metadata can be in textual chunks
            for (Tag tag : directory.getTags()) {
                String tagName = tag.getTagName();
                if (tagName != null && tagName.toLowerCase().contains("creation")) {
                    String dateStr = tag.getDescription();
                    if (dateStr != null) {
                        Date pngDate = parseFlexibleDate(dateStr);
                        if (pngDate != null && isValidDate(pngDate) && dateTaken == null) {
                            dateTaken = pngDate;
                            log.debug("Found PNG creation date: {}", pngDate);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Could not extract PNG date: {}", e.getMessage());
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

        log.debug("Could not parse XMP date: {}", dateStr);
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

    /**
     * Process GPS directory and extract location + GPS timestamp
     * Returns GPS date for priority comparison (GPS time is very reliable - from
     * satellites)
     */
    private Date processGpsDirectory(GpsDirectory directory) {
        GeoLocation geoLocation = directory.getGeoLocation();
        if (geoLocation != null) {
            latitude = geoLocation.getLatitude();
            longitude = geoLocation.getLongitude();
        }

        // Extract GPS timestamp (UTC time from satellites - highly reliable)
        Date gpsDate = directory.getGpsDate();
        if (gpsDate != null && isValidDate(gpsDate)) {
            log.debug("Found GPS date: {}", gpsDate);
            return gpsDate;
        }

        return null;
    }

    public void reorderDates() {
        List<Date> dates = new ArrayList<>();
        try {
            Date thresholdDate = DATE_FORMAT.parse("2006-01-01");

            if (dateTaken != null && !dateTaken.before(thresholdDate)) {
                dates.add(dateTaken);
            } else {
                dateTaken = null;
            }

            if (dateCreated != null && !dateCreated.before(thresholdDate)) {
                dates.add(dateCreated);
            } else {
                dateCreated = null;
            }

            if (dateModified != null && !dateModified.before(thresholdDate)) {
                dates.add(dateModified);
            } else {
                dateModified = null;
            }

            Collections.sort(dates);

            dateTaken = !dates.isEmpty() ? dates.get(0) : null;
            dateCreated = dates.size() > 1 ? dates.get(1) : null;
            dateModified = dates.size() > 2 ? dates.get(2) : null;

        } catch (ParseException e) {
            log.error("Failed to parse threshold date", e);
            imageErrorTracker.saveProgress("ReorderDates file: " + file);
        }
    }

    public boolean isImage() {
        return "image".equals(this.type);
    }

    public boolean isVideo() {
        return "video".equals(this.type);
    }

    public boolean isOther() {
        return "other".equals(this.type);
    }

    public boolean isAfter(ExifData existingFileData) {
        compressionTracker.saveProgress("Compared " + file.getAbsolutePath() +
                " $ to $ " + existingFileData.file.getAbsolutePath());

        if (dateTaken != null && existingFileData.dateTaken != null) {
            if (dateTaken.equals(existingFileData.dateTaken)) {
                if (dateCreated != null && existingFileData.dateCreated != null) {
                    return checkDateCreated(existingFileData);
                } else {
                    return dateCreated == null;
                }
            }
            return dateTaken.after(existingFileData.dateTaken);
        } else if (dateTaken != null) {
            return false;
        } else {
            if (dateCreated != null && existingFileData.dateCreated != null) {
                return checkDateCreated(existingFileData);
            } else if (dateCreated != null) {
                return false;
            } else {
                return checkDateModified(existingFileData);
            }
        }
    }

    private boolean checkDateCreated(ExifData existingFileData) {
        if (dateCreated.equals(existingFileData.dateCreated)) {
            return checkDateModified(existingFileData);
        }
        return dateCreated.after(existingFileData.dateCreated);
    }

    private boolean checkDateModified(ExifData existingFileData) {
        if (dateModified != null && existingFileData.dateModified != null) {
            return dateModified.after(existingFileData.dateModified);
        }
        return dateModified == null;
    }

    public void setImageExifDataType() {
        String fileName = file.getName();
        int dotIndex = fileName.lastIndexOf('.');
        extension = (dotIndex == -1) ? "" : fileName.substring(dotIndex + 1).toLowerCase();

        // Use FileTypeRegistry static sets which read from application.properties (via
        // MediaSortingProperties)
        if (FileTypeRegistry.IMAGE_EXTENSIONS.contains(extension)) {
            type = "image";
        } else if (FileTypeRegistry.VIDEO_EXTENSIONS.contains(extension)) {
            type = "video";
        } else {
            type = "other";
        }
    }

    public void determineFolderDate() {
        // Use the getEarliestDate() helper to avoid code duplication
        Date earliestDate = getEarliestDate();

        if (earliestDate != null) {
            folderDate = DATE_FORMAT.format(earliestDate);
        }
    }

    // Constructor initialization replaces PostConstruct

    public void logFileDetails(String message) {
        if (fileTracker != null) {
            fileTracker.saveProgress(file.getName() + "$" + file.length() + "$" +
                    file.getAbsolutePath() + "$" + deviceName + "$" +
                    deviceModel + "$" + dateTaken + "$" + dateCreated + "$" +
                    dateModified + "$" + latitude + "$" + longitude + "$" + message);
        }
    }

    // ========== Lombok @Data generates standard getters/setters for all fields
    // ==========
    // Custom accessor methods below provide additional functionality

    /**
     * Get GPS latitude (for geocoding)
     * Alias for getLatitude() for clarity in geocoding context
     */
    public Double getGpsLatitude() {
        return latitude;
    }

    /**
     * Get GPS longitude (for geocoding)
     * Alias for getLongitude() for clarity in geocoding context
     */
    public Double getGpsLongitude() {
        return longitude;
    }

    /**
     * Calculate quality score for comparison
     * Higher score = better quality
     */
    public int getQualityScore() {
        if (imageWidth != null && imageHeight != null) {
            // For images: resolution (total pixels)
            return imageWidth * imageHeight;
        } else if (fileSize != null) {
            // Fallback: file size
            return fileSize.intValue();
        }
        return 0;
    }

    /**
     * Get the earliest date among dateTaken, dateCreated, and dateModified
     * This is used for determining the true original file in duplicate detection
     * 
     * @return The earliest date, or null if no dates are available
     */
    public Date getEarliestDate() {
        Date earliestDate = null;

        if (dateTaken != null) {
            earliestDate = dateTaken;
        }

        if (dateCreated != null && (earliestDate == null || dateCreated.before(earliestDate))) {
            earliestDate = dateCreated;
        }

        if (dateModified != null && (earliestDate == null || dateModified.before(earliestDate))) {
            earliestDate = dateModified;
        }

        return earliestDate;
    }

    /**
     * Check if this file is better quality than another
     * Priority order:
     * 1. No OS-generated duplicate patterns = better (e.g., no "(1)", " - Copy
     * (2)")
     * 2. Older date = better (true original by capture time)
     * 3. Higher resolution = better (if dates are equal/unknown)
     * 4. Larger file size = better (last resort)
     */
    public boolean isBetterQualityThan(ExifData other) {
        if (other == null) {
            log.info("[QUALITY] {} is better (other is null)", this.file.getAbsolutePath());
            return true;
        }

        // For videos, delegate to VideoQualityComparator which uses different criteria
        // (prioritizes file size/bitrate over resolution)
        if (this.isVideo() && other.isVideo() && videoQualityComparator != null) {
            log.debug("[QUALITY] Delegating video comparison to VideoQualityComparator");
            return videoQualityComparator.isVideo1BetterQuality(this, other);
        }

        // For images and mixed comparisons, use image-optimized comparison below
        // Collect comparison data first
        String file1Path = this.file.getAbsolutePath();
        String file2Path = other.file.getAbsolutePath();

        // Priority 0: OS-generated duplicate pattern detection
        boolean thisHasCopyPattern = DuplicatePatternUtils
                .hasOSDuplicatePattern(this.file.getName());
        boolean otherHasCopyPattern = DuplicatePatternUtils
                .hasOSDuplicatePattern(other.file.getName());

        // Priority 1: Date comparison - use the EARLIEST date among all three dates
        Date thisDate = getEarliestDate();
        Date otherDate = other.getEarliestDate();

        // Priority 2: Resolution comparison
        int thisQuality = this.getQualityScore();
        int otherQuality = other.getQualityScore();

        // Format dates - show ALL dates to see which is earliest
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        // Format individual dates
        String thisDateTakenStr = this.dateTaken != null ? sdf.format(this.dateTaken) : "N/A";
        String otherDateTakenStr = other.dateTaken != null ? sdf.format(other.dateTaken) : "N/A";

        String thisDateCreatedStr = this.dateCreated != null ? sdf.format(this.dateCreated) : "N/A";
        String otherDateCreatedStr = other.dateCreated != null ? sdf.format(other.dateCreated) : "N/A";

        String thisDateModifiedStr = this.dateModified != null ? sdf.format(this.dateModified) : "N/A";
        String otherDateModifiedStr = other.dateModified != null ? sdf.format(other.dateModified) : "N/A";

        // Format earliest date (the one actually used)
        String thisDateStr = thisDate != null ? sdf.format(thisDate) : "N/A";
        String otherDateStr = otherDate != null ? sdf.format(otherDate) : "N/A";

        // Format file sizes
        String thisSize = formatFileSize(this.fileSize);
        String otherSize = formatFileSize(other.fileSize);

        // Format resolution
        String thisRes = (this.imageWidth != null && this.imageHeight != null)
                ? "%dx%d (%.1fMP)".formatted(this.imageWidth, this.imageHeight, thisQuality / 1_000_000.0)
                : "N/A";
        String otherRes = (other.imageWidth != null && other.imageHeight != null)
                ? "%dx%d (%.1fMP)".formatted(other.imageWidth, other.imageHeight, otherQuality / 1_000_000.0)
                : "N/A";

        // Start comparison table
        log.info("[QUALITY] ========================================================================");
        log.info("[QUALITY] Comparing: {}  vs  {}", file1Path, file2Path);
        log.info("[QUALITY] ------------------------------------------------------------------------");

        // Determine winner and reason with step-by-step logging
        boolean result;
        String reason;
        String winnerPath;

        // Step 1: Check Date FIRST - the file with the older timestamp is the original
        // This is more reliable than filename patterns which can be misleading
        log.info("[QUALITY] STEP 1 - Date Comparison (Primary Indicator)");
        log.info("[QUALITY]   File 1 Dates:");
        log.info("[QUALITY]     - Date Taken   : {}", thisDateTakenStr);
        log.info("[QUALITY]     - Date Created : {}", thisDateCreatedStr);
        log.info("[QUALITY]     - Date Modified: {}", thisDateModifiedStr);
        log.info("[QUALITY]     - EARLIEST     : {}", thisDateStr);
        log.info("[QUALITY]   File 2 Dates:");
        log.info("[QUALITY]     - Date Taken   : {}", otherDateTakenStr);
        log.info("[QUALITY]     - Date Created : {}", otherDateCreatedStr);
        log.info("[QUALITY]     - Date Modified: {}", otherDateModifiedStr);
        log.info("[QUALITY]     - EARLIEST     : {}", otherDateStr);

        if (thisDate != null && otherDate != null) {
            if (thisDate.before(otherDate)) {
                result = true;
                reason = "File 1 is older (original by date)";
                winnerPath = file1Path;
                log.info("[QUALITY]   >> DECIDED: File 1 wins (older date)");
            } else if (thisDate.after(otherDate)) {
                result = false;
                reason = "File 2 is older (original by date)";
                winnerPath = file2Path;
                log.info("[QUALITY]   >> DECIDED: File 2 wins (older date)");
            } else {
                log.info("[QUALITY]   >> TIE - same earliest dates, checking next...");

                // Step 2: Check Resolution
                log.info("[QUALITY] STEP 2 - Resolution");
                log.info("[QUALITY]   File 1: {}    File 2: {}", thisRes, otherRes);

                if (thisQuality > otherQuality) {
                    result = true;
                    reason = "File 1 has higher resolution";
                    winnerPath = file1Path;
                    log.info("[QUALITY]   >> DECIDED: File 1 wins (higher resolution)");
                } else if (otherQuality > thisQuality) {
                    result = false;
                    reason = "File 2 has higher resolution";
                    winnerPath = file2Path;
                    log.info("[QUALITY]   >> DECIDED: File 2 wins (higher resolution)");
                } else {
                    log.info("[QUALITY]   >> TIE - same resolution, checking next...");

                    // Step 3: Check File Size
                    log.info("[QUALITY] STEP 3 - File Size");
                    log.info("[QUALITY]   File 1: {}    File 2: {}", thisSize, otherSize);

                    if (this.fileSize != null && other.fileSize != null) {
                        if (this.fileSize > other.fileSize) {
                            result = true;
                            reason = "File 1 has larger file size";
                            winnerPath = file1Path;
                            log.info("[QUALITY]   >> DECIDED: File 1 wins (larger file size)");
                        } else if (this.fileSize < other.fileSize) {
                            result = false;
                            reason = "File 2 has larger file size";
                            winnerPath = file2Path;
                            log.info("[QUALITY]   >> DECIDED: File 2 wins (larger file size)");
                        } else {
                            log.info("[QUALITY]   >> TIE - same file size, checking next...");

                            // Step 4: Check Copy Pattern (FINAL TIEBREAKER)
                            // Only use filename pattern when everything else is equal
                            log.info("[QUALITY] STEP 4 - Copy Pattern (Final Tiebreaker)");
                            log.info("[QUALITY]   File 1: {}    File 2: {}", thisHasCopyPattern, otherHasCopyPattern);

                            if (thisHasCopyPattern && !otherHasCopyPattern) {
                                result = false;
                                reason = "File 2 has cleaner filename (no copy pattern) - used as tiebreaker";
                                winnerPath = file2Path;
                                log.info("[QUALITY]   >> DECIDED: File 2 wins (no copy pattern - tiebreaker)");
                            } else if (!thisHasCopyPattern && otherHasCopyPattern) {
                                result = true;
                                reason = "File 1 has cleaner filename (no copy pattern) - used as tiebreaker";
                                winnerPath = file1Path;
                                log.info("[QUALITY]   >> DECIDED: File 1 wins (no copy pattern - tiebreaker)");
                            } else {
                                // Absolute tie - both files are completely identical
                                result = true;
                                reason = "Files are 100% equivalent - defaulting to File 1 (keep first encountered)";
                                winnerPath = file1Path;
                                log.info("[QUALITY]   >> COMPLETE TIE - all parameters equal, defaulting to File 1");
                            }
                        }
                    } else {
                        result = true;
                        reason = "File size unavailable - defaulting to File 1";
                        winnerPath = file1Path;
                        log.info("[QUALITY]   >> WARNING: File size unavailable, defaulting to File 1");
                    }
                }
            }
        } else {
            // No date info available - use resolution/size/pattern
            log.info("[QUALITY]   >> Date unavailable for one or both files");

            // Step 2: Check Resolution
            log.info("[QUALITY] STEP 2 - Resolution");
            log.info("[QUALITY]   File 1: {}    File 2: {}", thisRes, otherRes);

            if (thisQuality > otherQuality) {
                result = true;
                reason = "File 1 has higher resolution";
                winnerPath = file1Path;
                log.info("[QUALITY]   >> DECIDED: File 1 wins (higher resolution)");
            } else if (otherQuality > thisQuality) {
                result = false;
                reason = "File 2 has higher resolution";
                winnerPath = file2Path;
                log.info("[QUALITY]   >> DECIDED: File 2 wins (higher resolution)");
            } else {
                log.info("[QUALITY]   >> TIE - same resolution, checking next...");

                // Step 3: Check File Size
                log.info("[QUALITY] STEP 3 - File Size");
                log.info("[QUALITY]   File 1: {}    File 2: {}", thisSize, otherSize);

                if (this.fileSize != null && other.fileSize != null) {
                    if (this.fileSize > other.fileSize) {
                        result = true;
                        reason = "File 1 has larger file size";
                        winnerPath = file1Path;
                        log.info("[QUALITY]   >> DECIDED: File 1 wins (larger file size)");
                    } else if (this.fileSize < other.fileSize) {
                        result = false;
                        reason = "File 2 has larger file size";
                        winnerPath = file2Path;
                        log.info("[QUALITY]   >> DECIDED: File 2 wins (larger file size)");
                    } else {
                        log.info("[QUALITY]   >> TIE - same file size, checking next...");

                        // Step 4: Check Copy Pattern (FINAL TIEBREAKER)
                        log.info("[QUALITY] STEP 4 - Copy Pattern (Final Tiebreaker)");
                        log.info("[QUALITY]   File 1: {}    File 2: {}", thisHasCopyPattern, otherHasCopyPattern);

                        if (thisHasCopyPattern && !otherHasCopyPattern) {
                            result = false;
                            reason = "File 2 has cleaner filename (no copy pattern) - used as tiebreaker";
                            winnerPath = file2Path;
                            log.info("[QUALITY]   >> DECIDED: File 2 wins (no copy pattern - tiebreaker)");
                        } else if (!thisHasCopyPattern && otherHasCopyPattern) {
                            result = true;
                            reason = "File 1 has cleaner filename (no copy pattern) - used as tiebreaker";
                            winnerPath = file1Path;
                            log.info("[QUALITY]   >> DECIDED: File 1 wins (no copy pattern - tiebreaker)");
                        } else {
                            result = true;
                            reason = "Files are equivalent - defaulting to File 1";
                            winnerPath = file1Path;
                            log.info("[QUALITY]   >> COMPLETE TIE - defaulting to File 1");
                        }
                    }
                } else {
                    result = true;
                    reason = "File size unavailable - defaulting to File 1";
                    winnerPath = file1Path;
                    log.info("[QUALITY]   >> WARNING: File size unavailable, defaulting to File 1");
                }
            }
        }

        log.info("[QUALITY] ------------------------------------------------------------------------");
        log.info("[QUALITY]  WINNER: {}", winnerPath);
        log.info("[QUALITY]  REASON: {}", reason);
        log.info("[QUALITY] ========================================================================");

        return result;
    }

    /**
     * Format file size for display
     */
    private String formatFileSize(Long bytes) {
        if (bytes == null)
            return "N/A";
        if (bytes < 1024)
            return bytes + " B";
        if (bytes < 1024 * 1024)
            return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024)
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    /**
     * Get image width (for resolution comparison)
     */
    public Integer getWidth() {
        return imageWidth;
    }

    /**
     * Get image height (for resolution comparison)
     */
    public Integer getHeight() {
        return imageHeight;
    }

}
