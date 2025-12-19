package com.media.sort.service;

import com.media.sort.model.ExifData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Service to resolve folder patterns with dynamic tokens
 * Supports tokens: {year}, {month}, {day}, {year-month}, {year-month-day},
 * {device}, {extension}, {location}, {owner}
 */
@Service
public class FolderPatternResolver {

    @Autowired(required = false)
    private GeocodingService geocodingService;

    @Autowired(required = false)
    private DeviceMappingService deviceMappingService;

    /**
     * Resolve folder pattern using file metadata
     * 
     * @param pattern  Pattern template (e.g.,
     *                 "{year}/{year-month}/{device}/{extension}")
     * @param exifData File metadata
     * @return Resolved folder path
     */
    public String resolvePath(String pattern, ExifData exifData) {
        if (pattern == null || exifData == null) {
            return null;
        }

        String resolved = pattern;
        Date date = getBestDate(exifData);

        if (date != null) {
            SimpleDateFormat yearFormat = new SimpleDateFormat("yyyy");
            SimpleDateFormat monthFormat = new SimpleDateFormat("MM");
            SimpleDateFormat dayFormat = new SimpleDateFormat("dd");
            SimpleDateFormat yearMonthFormat = new SimpleDateFormat("yyyy-MM");
            SimpleDateFormat fullDateFormat = new SimpleDateFormat("yyyy-MM-dd");

            resolved = resolved.replace("{year}", yearFormat.format(date));
            resolved = resolved.replace("{month}", monthFormat.format(date));
            resolved = resolved.replace("{day}", dayFormat.format(date));
            resolved = resolved.replace("{year-month}", yearMonthFormat.format(date));
            resolved = resolved.replace("{year-month-day}", fullDateFormat.format(date));
        }

        // Device tokens - skip folder if unknown
        String device = getDeviceName(exifData);
        if (device != null && !device.equals("Unknown")) {
            resolved = resolved.replace("{device}", device);
        } else {
            resolved = resolved.replace("{device}", ""); // Skip device folder
        }

        // Extension
        String extension = exifData.getExtension();
        if (extension != null) {
            resolved = resolved.replace("{extension}", extension.toLowerCase());
        }

        // Location from GPS coordinates - skip if unknown
        String location = null;
        if (geocodingService != null && geocodingService.isEnabled()) {
            Double latitude = exifData.getGpsLatitude();
            Double longitude = exifData.getGpsLongitude();
            if (latitude != null && longitude != null) {
                location = geocodingService.getLocation(latitude, longitude);
            }
        }
        if (location != null && !location.isEmpty()) {
            resolved = resolved.replace("{location}", location);
        } else {
            resolved = resolved.replace("{location}", ""); // Skip location folder
        }

        // Owner from device mapping - skip if unknown
        String owner = null;
        if (deviceMappingService != null && deviceMappingService.isEnabled()) {
            owner = deviceMappingService.getOwner(device);
        }
        if (owner != null && !owner.isEmpty()) {
            resolved = resolved.replace("{owner}", owner);
        } else {
            resolved = resolved.replace("{owner}", ""); // Skip owner folder
        }

        // Clean up multiple consecutive slashes
        resolved = resolved.replaceAll("/+", "/");
        // Remove trailing slash
        if (resolved.endsWith("/")) {
            resolved = resolved.substring(0, resolved.length() - 1);
        }
        // Remove leading slash
        if (resolved.startsWith("/")) {
            resolved = resolved.substring(1);
        }

        return resolved;
    }

    /**
     * Get the best available date from ExifData
     */
    private Date getBestDate(ExifData exifData) {
        if (exifData.getDateTaken() != null) {
            return exifData.getDateTaken();
        }
        if (exifData.getDateCreated() != null) {
            return exifData.getDateCreated();
        }
        if (exifData.getDateModified() != null) {
            return exifData.getDateModified();
        }
        return null;
    }

    /**
     * Get device name, combining make and model if available
     */
    private String getDeviceName(ExifData exifData) {
        String make = exifData.getDeviceName();
        String model = exifData.getDeviceModel();

        if (model != null && !model.isEmpty()) {
            return sanitizeForPath(model);
        }
        if (make != null && !make.isEmpty()) {
            return sanitizeForPath(make);
        }
        return "Unknown";
    }

    /**
     * Sanitize string for use in file path
     */
    private String sanitizeForPath(String input) {
        if (input == null) {
            return null;
        }
        // Remove characters that are invalid in filenames
        return input.replaceAll("[<>:\"/\\\\|?*]", "_").trim();
    }
}
