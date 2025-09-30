package com.media.sort.model;

import com.drew.imaging.ImageProcessingException;
import com.drew.imaging.ImageMetadataReader;
import com.drew.lang.GeoLocation;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.Tag;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
import com.media.sort.service.ProgressTracker;
import com.media.sort.service.VideoExifDataService;
import com.media.sort.service.FileTypeRegistry;
import com.media.sort.service.ProgressTrackerFactory;
import com.media.sort.util.FileTypeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

@Component
public class ExifData {
    
    private static final Logger logger = LoggerFactory.getLogger(ExifData.class);
    
    private ProgressTracker imageErrorTracker;
    private ProgressTracker compressionTracker;
    private ProgressTracker fileTracker;
    
    @Autowired
    private VideoExifDataService videoExifDataService;

    @Autowired
    private FileTypeRegistry fileTypeRegistry;
    
    @Autowired
    private ProgressTrackerFactory progressTrackerFactory;
    
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

    // Default constructor - trackers will be initialized via ProgressTrackerFactory
    public ExifData() {
        // Trackers will be set via setProgressTrackers method when ProgressTrackerFactory is available
        // This avoids hardcoded paths
    }

    public ExifData(File file) {
        this();
        processFile(file);
    }
    
    /**
     * Initialize progress trackers using ProgressTrackerFactory
     * This method should be called by services that have access to ProgressTrackerFactory
     */
    public void setProgressTrackers(ProgressTracker imageErrorTracker, 
                                  ProgressTracker compressionTracker, 
                                  ProgressTracker fileTracker) {
        this.imageErrorTracker = imageErrorTracker;
        this.compressionTracker = compressionTracker;
        this.fileTracker = fileTracker;
    }


    
    public void processFile(File file) {
        try {
            this.file = file;
            setImageExifDataType();
            
            if (isImage()) {
                processImageFile();
            } else if (isVideo()) {
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
        
        type = fileTypeRegistry.getFileType(extension);
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
    public File getFile() { return file; }
    public void setFile(File file) { this.file = file; }
    
    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }
    
    public String getDeviceModel() { return deviceModel; }
    public void setDeviceModel(String deviceModel) { this.deviceModel = deviceModel; }
    
    public Date getDateTaken() { return dateTaken; }
    public void setDateTaken(Date dateTaken) { this.dateTaken = dateTaken; }
    
    public Date getDateCreated() { return dateCreated; }
    public void setDateCreated(Date dateCreated) { this.dateCreated = dateCreated; }
    
    public Date getDateModified() { return dateModified; }
    public void setDateModified(Date dateModified) { this.dateModified = dateModified; }
    
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
    
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    
    public String getFolderDate() { return folderDate; }
    public void setFolderDate(String folderDate) { this.folderDate = folderDate; }
    
    public String getExtension() { return extension; }
    public void setExtension(String extension) { this.extension = extension; }
}