package com.media.sort.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Service for computing perceptual hashes (pHash) of images.
 * Uses Discrete Cosine Transform (DCT) based perceptual hashing.
 * 
 * This detects visually similar images regardless of:
 * - Different resolutions
 * - Minor edits
 * - Format conversions
 * - Slight color adjustments
 */
@Service
public class PerceptualHashService {

    private static final Logger logger = LoggerFactory.getLogger(PerceptualHashService.class);

    // Hash size (32x32 is a good balance of accuracy and speed)
    private static final int HASH_SIZE = 32;

    // Smaller image size for faster DCT computation
    private static final int SMALL_SIZE = 32;

    // Hamming distance threshold for similarity
    // Lower = more strict, Higher = more lenient
    // Typical good value: 10-15 for different resolutions
    private static final int SIMILARITY_THRESHOLD = 12;

    /**
     * Compute perceptual hash for an image file
     */
    public String computeHash(File imageFile) {
        try {
            BufferedImage image = ImageIO.read(imageFile);
            if (image == null) {
                logger.warn("Failed to read image: {}", imageFile.getAbsolutePath());
                return null;
            }

            // Step 1: Reduce size (32x32)
            BufferedImage smallImage = resize(image, SMALL_SIZE, SMALL_SIZE);

            // Step 2: Convert to grayscale
            double[][] grayPixels = toGrayscale(smallImage);

            // Step 3: Compute DCT
            double[][] dctVals = applyDCT(grayPixels);

            // Step 4: Reduce DCT (top-left 8x8)
            double[][] reducedDCT = reduceDCT(dctVals, 8);

            // Step 5: Compute average value
            double avg = computeAverage(reducedDCT);

            // Step 6: Generate hash based on average
            long hash = computeHash(reducedDCT, avg);

            return Long.toHexString(hash);

        } catch (IOException e) {
            logger.error("Error computing perceptual hash for: {}", imageFile.getAbsolutePath(), e);
            return null;
        } catch (Exception e) {
            logger.error("Unexpected error in perceptual hash computation: {}", imageFile.getAbsolutePath(), e);
            return null;
        }
    }

    /**
     * Check if two images are perceptually similar
     */
    public boolean areSimilar(String hash1, String hash2) {
        if (hash1 == null || hash2 == null) {
            return false;
        }

        if (hash1.equals(hash2)) {
            return true; // Exact match
        }

        int distance = calculateHammingDistance(hash1, hash2);
        boolean similar = distance <= SIMILARITY_THRESHOLD;

        if (similar) {
            logger.debug("Images are perceptually similar (Hamming distance: {})", distance);
        }

        return similar;
    }

    /**
     * Calculate Hamming distance between two hashes
     */
    public int calculateHammingDistance(String hash1, String hash2) {
        try {
            long val1 = Long.parseUnsignedLong(hash1, 16);
            long val2 = Long.parseUnsignedLong(hash2, 16);
            long xor = val1 ^ val2;
            return Long.bitCount(xor);
        } catch (NumberFormatException e) {
            logger.error("Invalid hash format", e);
            return Integer.MAX_VALUE;
        }
    }

    // Helper methods

    private BufferedImage resize(BufferedImage image, int width, int height) {
        Image tmp = image.getScaledInstance(width, height, Image.SCALE_SMOOTH);
        BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = resized.createGraphics();
        g2d.drawImage(tmp, 0, 0, null);
        g2d.dispose();
        return resized;
    }

    private double[][] toGrayscale(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        double[][] gray = new double[height][width];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                // Standard RGB to grayscale conversion
                gray[y][x] = 0.299 * r + 0.587 * g + 0.114 * b;
            }
        }

        return gray;
    }

    private double[][] applyDCT(double[][] input) {
        int N = input.length;
        double[][] output = new double[N][N];

        for (int u = 0; u < N; u++) {
            for (int v = 0; v < N; v++) {
                double sum = 0.0;
                for (int i = 0; i < N; i++) {
                    for (int j = 0; j < N; j++) {
                        sum += input[i][j] *
                                Math.cos(((2 * i + 1) / (2.0 * N)) * u * Math.PI) *
                                Math.cos(((2 * j + 1) / (2.0 * N)) * v * Math.PI);
                    }
                }

                double cu = (u == 0) ? 1 / Math.sqrt(2.0) : 1.0;
                double cv = (v == 0) ? 1 / Math.sqrt(2.0) : 1.0;
                output[u][v] = 0.25 * cu * cv * sum;
            }
        }

        return output;
    }

    private double[][] reduceDCT(double[][] dct, int size) {
        double[][] reduced = new double[size][size];
        for (int i = 0; i < size; i++) {
            System.arraycopy(dct[i], 0, reduced[i], 0, size);
        }
        return reduced;
    }

    private double computeAverage(double[][] values) {
        double sum = 0.0;
        int count = 0;

        for (double[] row : values) {
            for (double val : row) {
                sum += val;
                count++;
            }
        }

        return sum / count;
    }

    private long computeHash(double[][] values, double avg) {
        long hash = 0;
        int bitIndex = 0;

        for (double[] row : values) {
            for (double val : row) {
                if (val > avg) {
                    hash |= (1L << bitIndex);
                }
                bitIndex++;
                if (bitIndex >= 64) {
                    return hash; // Limit to 64 bits
                }
            }
        }

        return hash;
    }
}
