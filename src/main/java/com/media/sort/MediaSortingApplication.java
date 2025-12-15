package com.media.sort;

import com.media.sort.config.LogDirectoryInitializer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(MediaSortingProperties.class)
public class MediaSortingApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(MediaSortingApplication.class);
        app.addInitializers(new LogDirectoryInitializer());
        app.run(args);
    }
}