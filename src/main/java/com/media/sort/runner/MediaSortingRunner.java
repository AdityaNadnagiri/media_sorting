package com.media.sort.runner;

import com.media.sort.MediaSortingProperties;
import com.media.sort.service.PhotoOrganizerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class MediaSortingRunner implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(MediaSortingRunner.class);

    @Autowired
    private MediaSortingProperties properties;

    @Autowired
    private PhotoOrganizerService photoOrganizerService;

    @Override
    public void run(String... args) throws Exception {
        logger.info("Starting Media Sorting Application");
        logger.info("Source folder: {}", properties.getSourceFolder());
        
        try {
            photoOrganizerService.organizePhotos(properties.getSourceFolder());
            logger.info("Media sorting completed successfully!");
        } catch (Exception e) {
            logger.error("Error during media sorting", e);
            throw e;
        }
    }
}