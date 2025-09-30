package com.media.sort;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(MediaSortingProperties.class)
public class MediaSortingApplication {

    public static void main(String[] args) {
        SpringApplication.run(MediaSortingApplication.class, args);
    }
}