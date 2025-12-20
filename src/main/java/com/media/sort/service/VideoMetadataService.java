package com.media.sort.service;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.Tag;
import com.drew.metadata.mov.QuickTimeDirectory;
import com.media.sort.model.ExifData;
import org.apache.tika.exception.TikaException;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.mp4.MP4Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.xml.sax.SAXException;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class VideoMetadataService {

    private static final Logger logger = LoggerFactory.getLogger(VideoMetadataService.class);

    private ProgressTracker videoErrorTracker;
    private ProgressTracker mp4ErrorTracker;
    private ProgressTracker tgpErrorTracker;
    private ProgressTracker qtErrorTracker;
    private ProgressTracker otherErrorTracker;

    private static final String[] POSSIBLE_CREATION_DATE_KEYS = {
            "xmpDM:creationDate", // XMPDM schema
            "meta:creation-date", // General metadata
            "Creation-Date",
            "dcterms:created"
    };

    @Autowired
    private ProgressTrackerFactory progressTrackerFactory;

    public VideoMetadataService() {
        // Trackers will be initialized through initializeTrackers method
    }

    private void initializeTrackers() {
        if (progressTrackerFactory != null) {
            this.videoErrorTracker = progressTrackerFactory.getVideoErrorTracker();
            this.mp4ErrorTracker = progressTrackerFactory.getVideoMp4ErrorTracker();
            this.tgpErrorTracker = progressTrackerFactory.getVideoTgpErrorTracker();
            this.qtErrorTracker = progressTrackerFactory.getVideoQtErrorTracker();
            this.otherErrorTracker = progressTrackerFactory.getVideoOtherErrorTracker();
        }
    }

    public void processVideoFile(ExifData exifData) throws IOException {
        // Initialize trackers if not already done
        if (videoErrorTracker == null) {
            initializeTrackers();
        }

        File file = exifData.getFile();
        String extension = exifData.getExtension().toLowerCase();

        BasicFileAttributes attr = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
        exifData.setDateTaken(null);
        exifData.setDateCreated(new Date(attr.creationTime().toMillis()));
        exifData.setDateModified(new Date(attr.lastModifiedTime().toMillis()));

        logger.debug("Processing video file: {} ({})", file.getName(), extension);
        logger.debug("  Initial dates - Created: {}, Modified: {}",
                exifData.getDateCreated(), exifData.getDateModified());

        try {
            // Try format-specific metadata extraction
            if ("mov".equals(extension)) {
                extractQuickTimeMetadata(exifData);
            }
            if (Arrays.asList("mp4", "m4v").contains(extension) || exifData.getDateTaken() == null) {
                extractMp4Metadata(exifData);
            }
            if ("3gp".equals(extension) || exifData.getDateTaken() == null) {
                extract3gpMetadata(exifData);
            }
            if (Arrays.asList("avi", "mkv", "dat", "wlmp", "mpg", "wmv").contains(extension) ||
                    exifData.getDateTaken() == null) {
                extractOtherVideoMetadata(exifData);
            }

            // Log extracted date
            if (exifData.getDateTaken() != null) {
                logger.info(" Extracted DateTaken for {}: {}", file.getName(), exifData.getDateTaken());
            } else {
                logger.warn(" No DateTaken found for video: {} - will use filesystem dates",
                        file.getName());
                videoErrorTracker.saveProgress("No Date processVideoFile file: " + file);

                // IMPORTANT: Even though we have filesystem dates (dateCreated, dateModified),
                // log this as a warning so we know metadata extraction failed
                // The getEarliestDate() will use dateCreated/dateModified as fallback
            }

            // Log final date situation
            Date earliestDate = exifData.getEarliestDate();
            if (earliestDate != null) {
                logger.debug("  Final earliest date for {}: {} (source: {})",
                        file.getName(),
                        earliestDate,
                        earliestDate.equals(exifData.getDateTaken()) ? "metadata" : "filesystem");
            } else {
                logger.error("  CRITICAL: No date available for video: {}", file.getName());
            }

        } catch (RuntimeException e) {
            logger.error("Unexpected error processing video file: {}", file.getAbsolutePath(), e);
        }
    }

    private void extractOtherVideoMetadata(ExifData exifData) {
        File file = exifData.getFile();
        try (InputStream input = new FileInputStream(file)) {
            BodyContentHandler handler = new BodyContentHandler();
            org.apache.tika.metadata.Metadata metadata = new org.apache.tika.metadata.Metadata();
            AutoDetectParser parser = new AutoDetectParser();
            ParseContext parseContext = new ParseContext();
            parser.parse(input, handler, metadata, parseContext);

            for (String key : POSSIBLE_CREATION_DATE_KEYS) {
                String creationDate = metadata.get(key);
                if (creationDate != null) {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
                    exifData.setDateTaken(sdf.parse(creationDate));
                    break;
                }
            }

            if (exifData.getDateTaken() == null) {
                otherErrorTracker.saveProgress("No Date extractOtherVideoMetadata file: " + file);
            }
        } catch (IOException | ParseException | TikaException | SAXException e) {
            logger.error("Failed to extract other video metadata for file: {}", file.getAbsolutePath(), e);
            otherErrorTracker.saveProgress("extractOtherVideoMetadata file: " + file);
        }
    }

    private void extractQuickTimeMetadata(ExifData exifData) throws IOException {
        File file = exifData.getFile();
        try {
            Metadata drewMetadata = ImageMetadataReader.readMetadata(file);
            QuickTimeDirectory directory = drewMetadata.getFirstDirectoryOfType(QuickTimeDirectory.class);

            if (directory != null) {
                exifData.setDateTaken(directory.getDate(QuickTimeDirectory.TAG_CREATION_TIME));

                // Extract device information (Make and Model)
                for (Tag tag : directory.getTags()) {
                    String tagName = tag.getTagName();
                    if ("Make".equalsIgnoreCase(tagName) && exifData.getDeviceName() == null) {
                        String make = tag.getDescription();
                        if (make != null && !make.trim().isEmpty()) {
                            exifData.setDeviceName(make.trim());
                        }
                    } else if ("Model".equalsIgnoreCase(tagName) && exifData.getDeviceModel() == null) {
                        String model = tag.getDescription();
                        if (model != null && !model.trim().isEmpty()) {
                            exifData.setDeviceModel(model.trim());
                        }
                    }
                }
            }

            if (exifData.getDateTaken() == null) {
                qtErrorTracker.saveProgress("No Date extractQuickTimeMetadata file: " + file);
            }
        } catch (ImageProcessingException e) {
            // Expected for many MOV files - fallback methods will be used
            logger.debug("QuickTime metadata not available for: {} (will try fallback methods)", file.getName());
            qtErrorTracker.saveProgress("extractQuickTimeMetadata file: " + file);
        }
    }

    private void extractMp4Metadata(ExifData exifData) {
        File file = exifData.getFile();
        try (InputStream input = new FileInputStream(file)) {
            Metadata drewMetadata = ImageMetadataReader.readMetadata(file);

            for (Directory directory : drewMetadata.getDirectories()) {
                if ("MP4".equals(directory.getName())) {
                    for (Tag tag : directory.getTags()) {
                        String tagName = tag.getTagName();
                        if ("Creation Time".equals(tagName)) {
                            DateTimeFormatter formatter = DateTimeFormatter
                                    .ofPattern("EEE MMM dd HH:mm:ss zzz yyyy", Locale.ENGLISH);
                            ZonedDateTime dateTime = ZonedDateTime.parse(tag.getDescription(), formatter);
                            exifData.setDateTaken(Date.from(dateTime.toInstant()));
                        } else if ("Make".equalsIgnoreCase(tagName) && exifData.getDeviceName() == null) {
                            String make = tag.getDescription();
                            if (make != null && !make.trim().isEmpty()) {
                                exifData.setDeviceName(make.trim());
                            }
                        } else if ("Model".equalsIgnoreCase(tagName) && exifData.getDeviceModel() == null) {
                            String model = tag.getDescription();
                            if (model != null && !model.trim().isEmpty()) {
                                exifData.setDeviceModel(model.trim());
                            }
                        }
                    }
                }
                if (exifData.getDateTaken() != null) {
                    break;
                }
            }

            // Also try extracting from Tika metadata as fallback
            if (exifData.getDeviceName() == null || exifData.getDeviceModel() == null) {
                try {
                    BodyContentHandler handler = new BodyContentHandler();
                    org.apache.tika.metadata.Metadata tikaMetadata = new org.apache.tika.metadata.Metadata();
                    AutoDetectParser parser = new AutoDetectParser();
                    ParseContext parseContext = new ParseContext();

                    try (InputStream tikaInput = new FileInputStream(file)) {
                        parser.parse(tikaInput, handler, tikaMetadata, parseContext);

                        if (exifData.getDeviceName() == null) {
                            String make = tikaMetadata.get("Make");
                            if (make != null && !make.trim().isEmpty()) {
                                exifData.setDeviceName(make.trim());
                            }
                        }

                        if (exifData.getDeviceModel() == null) {
                            String model = tikaMetadata.get("Model");
                            if (model != null && !model.trim().isEmpty()) {
                                exifData.setDeviceModel(model.trim());
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.debug("Could not extract device info from Tika for: {}", file.getName());
                }
            }

            if (exifData.getDateTaken() == null) {
                mp4ErrorTracker.saveProgress("No Date extractMp4Metadata file: " + file);
            }
        } catch (IOException | ImageProcessingException e) {
            // Expected for many video files - fallback methods will be used
            logger.debug("MP4 metadata not available for: {} (will try fallback methods)", file.getName());
            mp4ErrorTracker.saveProgress("extractMp4Metadata file: " + file);
        }
    }

    private void extract3gpMetadata(ExifData exifData) {
        File file = exifData.getFile();
        try (InputStream input = new FileInputStream(file)) {
            BodyContentHandler handler = new BodyContentHandler();
            org.apache.tika.metadata.Metadata tikaMetadata = new org.apache.tika.metadata.Metadata();
            MP4Parser parser = new MP4Parser();
            ParseContext parseCtx = new ParseContext();
            parser.parse(input, handler, tikaMetadata, parseCtx);

            for (String key : POSSIBLE_CREATION_DATE_KEYS) {
                String creationDate = tikaMetadata.get(key);
                if (creationDate != null) {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
                    exifData.setDateTaken(sdf.parse(creationDate));
                    break;
                }
            }

            if (exifData.getDateTaken() == null) {
                tgpErrorTracker.saveProgress("No Date extract3gpMetadata file: " + file);
            }
        } catch (IOException | SAXException | TikaException | ParseException e) {
            logger.error("Failed to extract 3GP metadata for file: {}", file.getAbsolutePath(), e);
            tgpErrorTracker.saveProgress("extract3gpMetadata file: " + file);
        }
    }
}