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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 */
public class ExifData {

    private static final Logger logger = LoggerFactory.getLogger(ExifData.class);

    private ProgressTracker imageErrorTracker;
    private ProgressTracker compressionTracker;
    private ProgressTracker fileTracker;

    // Dependencies passed via setter methods
    private VideoExifDataService videoExifDataService;

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
            logger.error("Failed to process file: {}", file.getAbsolutePath(), e);
            this.type = "other";
            imageErrorTracker.saveProgress("ExifData file: " + file);
        }
    }

    private void processImageFile() throws IOException, ImageProcessingException {
        Metadata metadata = ImageMetadataReader.readMetadata(file);

        BasicFileAttributes attr = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
        this.dateCreated = new Date(attr.creationTime().toMillis());
        this.dateModified = new Date(attr.lastModifiedTime().toMillis());

        for (Directory directory : metadata.getDirectories()) {
            if (directory instanceof ExifSubIFDDirectory) {
                processExifDirectory((ExifSubIFDDirectory) directory);
            } else if (directory instanceof GpsDirectory && !directory.getTags().isEmpty()) {
                processGpsDirectory((GpsDirectory) directory);
            }

            if (deviceName != null && deviceModel != null && dateTaken != null &&
                    latitude != null && longitude != null) {
                break;
            }
        }
    }

    private void processExifDirectory(ExifSubIFDDirectory directory) {
        if (dateTaken == null) {
            dateTaken = directory.getDateOriginal();
        }

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
    }

    private void processGpsDirectory(GpsDirectory directory) {
        GeoLocation geoLocation = directory.getGeoLocation();
        if (geoLocation != null) {
            latitude = geoLocation.getLatitude();
            longitude = geoLocation.getLongitude();
        }
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
            logger.error("Failed to parse threshold date", e);
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
        if (dateTaken != null) {
            folderDate = DATE_FORMAT.format(dateTaken);
        } else if (dateCreated != null) {
            folderDate = DATE_FORMAT.format(dateCreated);
        } else if (dateModified != null) {
            folderDate = DATE_FORMAT.format(dateModified);
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

    // Getters and setters
    public File getFile() {
        return file;
    }

    public void setFile(File file) {
        this.file = file;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public String getDeviceModel() {
        return deviceModel;
    }

    public void setDeviceModel(String deviceModel) {
        this.deviceModel = deviceModel;
    }

    public Date getDateTaken() {
        return dateTaken;
    }

    public void setDateTaken(Date dateTaken) {
        this.dateTaken = dateTaken;
    }

    public Date getDateCreated() {
        return dateCreated;
    }

    public void setDateCreated(Date dateCreated) {
        this.dateCreated = dateCreated;
    }

    public Date getDateModified() {
        return dateModified;
    }

    public void setDateModified(Date dateModified) {
        this.dateModified = dateModified;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getFolderDate() {
        return folderDate;
    }

    public void setFolderDate(String folderDate) {
        this.folderDate = folderDate;
    }

    public String getExtension() {
        return extension;
    }

    public void setExtension(String extension) {
        this.extension = extension;
    }

    // Perceptual hash and quality getters/setters
    public String getPerceptualHash() {
        return perceptualHash;
    }

    public void setPerceptualHash(String perceptualHash) {
        this.perceptualHash = perceptualHash;
    }

    public Integer getImageWidth() {
        return imageWidth;
    }

    public void setImageWidth(Integer imageWidth) {
        this.imageWidth = imageWidth;
    }

    public Integer getImageHeight() {
        return imageHeight;
    }

    public void setImageHeight(Integer imageHeight) {
        this.imageHeight = imageHeight;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
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
            logger.info("[QUALITY] {} is better (other is null)", this.file.getName());
            return true;
        }

        logger.info("[QUALITY] Comparing {} vs {}", this.file.getName(), other.file.getName());

        // Priority 0: OS-generated duplicate pattern detection
        // Very specific patterns that operating systems use for duplicates
        boolean thisHasCopyPattern = hasOSDuplicatePattern(this.file.getName());
        boolean otherHasCopyPattern = hasOSDuplicatePattern(other.file.getName());

        logger.info("[QUALITY]   Pattern check: {} hasCopy={}, {} hasCopy={}",
                this.file.getName(), thisHasCopyPattern,
                other.file.getName(), otherHasCopyPattern);

        if (thisHasCopyPattern && !otherHasCopyPattern) {
            logger.info("[QUALITY]   Result: {} is WORSE (has copy pattern)", this.file.getName());
            return false; // This has copy pattern, other is clean = other is better
        } else if (!thisHasCopyPattern && otherHasCopyPattern) {
            logger.info("[QUALITY]   Result: {} is BETTER (other has copy pattern)", this.file.getName());
            return true; // This is clean, other has copy pattern = this is better
        }
        // If both have patterns or both are clean, continue to date comparison

        // Priority 1: Date comparison (older is better - it's the true original)
        Date thisDate = this.dateTaken != null ? this.dateTaken
                : (this.dateCreated != null ? this.dateCreated : this.dateModified);
        Date otherDate = other.dateTaken != null ? other.dateTaken
                : (other.dateCreated != null ? other.dateCreated : other.dateModified);

        logger.info("[QUALITY]   Date check: {} date={}, {} date={}",
                this.file.getName(), thisDate,
                other.file.getName(), otherDate);

        if (thisDate != null && otherDate != null) {
            // If this file is OLDER (before other), it's the better original
            if (thisDate.before(otherDate)) {
                logger.info("[QUALITY]   Result: {} is BETTER (older date)", this.file.getName());
                return true; // This is older = better (true original)
            } else if (thisDate.after(otherDate)) {
                logger.info("[QUALITY]   Result: {} is WORSE (newer date)", this.file.getName());
                return false; // This is newer = worse (likely a copy/edit)
            }
            logger.info("[QUALITY]   Dates are equal, checking resolution...");
            // If dates are equal, fall through to resolution comparison
        }

        // Priority 2: Resolution comparison (for perceptual duplicates with
        // same/unknown dates)
        int thisQuality = this.getQualityScore();
        int otherQuality = other.getQualityScore();

        logger.info("[QUALITY]   Quality scores: {} = {}px ({}x{}), {} = {}px ({}x{})",
                this.file.getName(), thisQuality, this.imageWidth, this.imageHeight,
                other.file.getName(), otherQuality, other.imageWidth, other.imageHeight);

        boolean result = thisQuality > otherQuality;
        logger.info("[QUALITY]   Result: {} is {} (by resolution)",
                this.file.getName(), result ? "BETTER" : "WORSE");

        return result;
    }

    /**
     * Check if filename contains OS-generated duplicate patterns
     * Matches very specific patterns like: (1), (2), - Copy, copy1, etc.
     */
    private boolean hasOSDuplicatePattern(String filename) {
        if (filename == null) {
            return false;
        }

        // Match patterns like: "(1)", "(2)", " (1)", " (2)", etc.
        if (filename.matches(".*\\s*\\(\\d+\\).*")) {
            return true;
        }

        String lower = filename.toLowerCase();

        // Windows/Mac copy patterns
        return lower.contains(" - copy") || // "Photo - Copy.jpg"
                lower.contains("- copy (") || // "Photo - Copy (2).jpg"
                lower.contains(" copy ") || // "Photo copy 1.jpg"
                lower.matches(".*copy\\d+.*") || // "Photocopy1.jpg"
                lower.matches(".*copy_\\d+.*") || // "Photo_copy_1.jpg"
                lower.matches(".*\\dcopy\\d.*"); // "Photo1copy1.jpg"
    }
}