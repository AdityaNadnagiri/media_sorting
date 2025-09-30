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
public class VideoExifDataService {
    
    private static final Logger logger = LoggerFactory.getLogger(VideoExifDataService.class);
    
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
    
    public VideoExifDataService() {
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
        File file = exifData.getFile();
        String extension = exifData.getExtension().toLowerCase();

        BasicFileAttributes attr = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
        exifData.setDateTaken(null);
        exifData.setDateCreated(new Date(attr.creationTime().toMillis()));
        exifData.setDateModified(new Date(attr.lastModifiedTime().toMillis()));

        try {
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
            
            if (exifData.getDateTaken() == null) {
                logger.warn("No creation date found for video file: {}", file.getAbsolutePath());
                videoErrorTracker.saveProgress("No Date processVideoFile file: " + file);
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
            }
            
            if (exifData.getDateTaken() == null) {
                qtErrorTracker.saveProgress("No Date extractQuickTimeMetadata file: " + file);
            }
        } catch (ImageProcessingException e) {
            logger.error("Failed to extract QuickTime metadata for file: {}", file.getAbsolutePath(), e);
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
                        if ("Creation Time".equals(tag.getTagName())) {
                            DateTimeFormatter formatter = DateTimeFormatter
                                .ofPattern("EEE MMM dd HH:mm:ss zzz yyyy", Locale.ENGLISH);
                            ZonedDateTime dateTime = ZonedDateTime.parse(tag.getDescription(), formatter);
                            exifData.setDateTaken(Date.from(dateTime.toInstant()));
                            break;
                        }
                    }
                }
                if (exifData.getDateTaken() != null) {
                    break;
                }
            }
            
            if (exifData.getDateTaken() == null) {
                mp4ErrorTracker.saveProgress("No Date extractMp4Metadata file: " + file);
            }
        } catch (IOException | ImageProcessingException e) {
            logger.error("Failed to extract MP4 metadata for file: {}", file.getAbsolutePath(), e);
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