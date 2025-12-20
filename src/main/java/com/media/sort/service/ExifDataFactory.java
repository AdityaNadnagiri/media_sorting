package com.media.sort.service;

import com.media.sort.model.ExifData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;

/**
 * Factory service for creating ExifData instances with all dependencies
 * properly injected.
 * This ensures metadata extraction services are available for both images and
 * videos.
 */
@Service
public class ExifDataFactory {

    @Autowired
    private ImageMetadataService imageMetadataService;

    @Autowired
    private VideoMetadataService videoMetadataService;

    @Autowired
    private VideoQualityComparator videoQualityComparator;

    @Autowired
    private ProgressTrackerFactory progressTrackerFactory;

    /**
     * Create an ExifData instance with all dependencies injected
     *
     * @param file The media file to process
     * @return ExifData instance with metadata extracted
     */
    public ExifData createExifData(File file) {
        ExifData exifData = new ExifData();

        // Inject metadata extraction services
        exifData.setImageMetadataService(imageMetadataService);
        exifData.setVideoMetadataService(videoMetadataService);
        exifData.setVideoQualityComparator(videoQualityComparator);

        // Inject progress trackers
        exifData.setProgressTrackers(
                progressTrackerFactory.getImageErrorTracker(),
                progressTrackerFactory.getFileComparisonTracker(),
                progressTrackerFactory.getFileComparisonTracker());

        // Process the file
        exifData.processFile(file);

        return exifData;
    }
}
