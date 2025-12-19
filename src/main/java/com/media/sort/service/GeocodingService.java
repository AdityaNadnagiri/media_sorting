package com.media.sort.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Service to convert GPS coordinates to location names using geocoding.
 * Uses OpenStreetMap Nominatim API (free, no API key required).
 * Modernized with Java 21 HttpClient and Jackson JSON parsing.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class GeocodingService {

    private static final String NOMINATIM_URL = "https://nominatim.openstreetmap.org/reverse";

    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Value("${media.geocoding.enabled:false}")
    private boolean enabled;

    @Value("${media.geocoding.timeout-ms:5000}")
    private int timeoutMs;

    @Value("${media.geocoding.user-agent:MediaSortingApp/1.0}")
    private String userAgent;

    /**
     * Convert GPS coordinates to location name using modern HttpClient and Jackson
     */
    public String getLocation(double latitude, double longitude) {
        if (!enabled) {
            return null;
        }

        try {
            // Use Java 15+ formatted strings
            String url = "%s?lat=%f&lon=%f&format=json&addressdetails=1"
                    .formatted(NOMINATIM_URL, latitude, longitude);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", userAgent)
                    .timeout(Duration.ofMillis(timeoutMs))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return parseLocationFromJson(response.body());
            } else {
                log.warn("Geocoding failed with HTTP {}", response.statusCode());
                return null;
            }
        } catch (Exception e) {
            log.debug("Geocoding error for ({}, {}): {}", latitude, longitude, e.getMessage());
            return null;
        }
    }

    /**
     * Parse location from JSON response using Jackson
     */
    private String parseLocationFromJson(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            String displayName = root.path("display_name").asText(null);

            if (displayName == null) {
                return null;
            }

            // Simplify - take only city, country (first and last parts)
            String[] parts = displayName.split(",");
            if (parts.length >= 3) {
                return "%s, %s".formatted(parts[0].trim(), parts[parts.length - 1].trim());
            }

            return displayName;
        } catch (Exception e) {
            log.debug("Error parsing geocoding response: {}", e.getMessage());
            return null;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }
}
