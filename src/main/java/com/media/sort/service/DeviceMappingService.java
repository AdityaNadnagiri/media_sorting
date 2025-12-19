package com.media.sort.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Service to map camera/phone models to owner names
 * Configuration format: device.{model}={owner}
 */
@Service
public class DeviceMappingService {

    private static final Logger logger = LoggerFactory.getLogger(DeviceMappingService.class);

    @Value("${media.device-mapping.enabled:false}")
    private boolean enabled;

    private final Map<String, String> deviceOwnerMap = new HashMap<>();

    /**
     * Get owner name for a given device
     */
    public String getOwner(String deviceName) {
        if (!enabled || deviceName == null || deviceName.isEmpty()) {
            return null;
        }

        // Normalize device name (lowercase, remove special chars)
        String normalized = normalizeDeviceName(deviceName);

        // Check exact match first
        String owner = deviceOwnerMap.get(normalized);
        if (owner != null) {
            return owner;
        }

        // Check partial matches
        for (Map.Entry<String, String> entry : deviceOwnerMap.entrySet()) {
            if (normalized.contains(entry.getKey()) || entry.getKey().contains(normalized)) {
                logger.debug("Partial device match: {} -> {}", deviceName, entry.getValue());
                return entry.getValue();
            }
        }

        return null;
    }

    /**
     * Register a device-to-owner mapping
     */
    public void registerDevice(String deviceName, String owner) {
        if (deviceName != null && owner != null) {
            String normalized = normalizeDeviceName(deviceName);
            deviceOwnerMap.put(normalized, owner);
            logger.info("Registered device mapping: {} -> {}", deviceName, owner);
        }
    }

    /**
     * Normalize device name for matching
     */
    private String normalizeDeviceName(String deviceName) {
        return deviceName.toLowerCase()
                .replaceAll("[^a-z0-9]", "")
                .trim();
    }

    /**
     * Load device mappings from configuration
     * Format: "iphone14=John,canoneosr5=Sarah"
     */
    @Value("${media.device-mapping.mappings:}")
    public void loadMappings(String mappingsConfig) {
        if (mappingsConfig == null || mappingsConfig.isEmpty()) {
            logger.debug("No device mappings configured");
            return;
        }

        String[] mappings = mappingsConfig.split(",");
        for (String mapping : mappings) {
            String[] parts = mapping.split("=");
            if (parts.length == 2) {
                registerDevice(parts[0].trim(), parts[1].trim());
            }
        }
        logger.info("Loaded {} device mappings", deviceOwnerMap.size());
    }

    public boolean isEnabled() {
        return enabled;
    }
}
