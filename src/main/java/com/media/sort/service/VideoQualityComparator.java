package com.media.sort.service;

import com.media.sort.model.ExifData;
import com.media.sort.util.DuplicatePatternUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Service to compare two video files and determine which is higher quality.
 * 
 * Video comparison criteria differ from images:
 * - Bitrate and codec quality are more important than resolution for videos
 * - File size is a stronger indicator of quality for videos
 * - Frame rate (fps) can be a quality indicator
 * 
 * Priority order for video comparison:
 * 1. Date comparison (oldest is original)
 * 2. File size (larger is usually better for same codec)
 * 3. Resolution (higher is better)
 * 4. Copy pattern (tiebreaker)
 */
@Slf4j
@Service
public class VideoQualityComparator {

    /**
     * Compare two video files and determine which is better quality
     * 
     * @param video1 First video's EXIF data
     * @param video2 Second video's EXIF data
     * @return true if video1 is better quality, false otherwise
     */
    public boolean isVideo1BetterQuality(ExifData video1, ExifData video2) {
        if (video2 == null) {
            log.info("[VIDEO-QUALITY] {} is better (other is null)", video1.getFile().getAbsolutePath());
            return true;
        }

        // Collect comparison data
        String file1Path = video1.getFile().getAbsolutePath();
        String file2Path = video2.getFile().getAbsolutePath();

        boolean video1HasCopyPattern = DuplicatePatternUtils
                .hasOSDuplicatePattern(video1.getFile().getName());
        boolean video2HasCopyPattern = DuplicatePatternUtils
                .hasOSDuplicatePattern(video2.getFile().getName());

        Date video1Date = video1.getEarliestDate();
        Date video2Date = video2.getEarliestDate();

        Long size1 = video1.getFileSize();
        Long size2 = video2.getFileSize();

        Integer width1 = video1.getImageWidth();
        Integer height1 = video1.getImageHeight();
        Integer width2 = video2.getImageWidth();
        Integer height2 = video2.getImageHeight();

        // Format for logging
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String video1DateStr = video1Date != null ? sdf.format(video1Date) : "N/A";
        String video2DateStr = video2Date != null ? sdf.format(video2Date) : "N/A";
        String size1Str = formatFileSize(size1);
        String size2Str = formatFileSize(size2);
        String res1Str = (width1 != null && height1 != null) ? width1 + "x" + height1 : "N/A";
        String res2Str = (width2 != null && height2 != null) ? width2 + "x" + height2 : "N/A";

        // Start comparison logging
        log.info("[VIDEO-QUALITY] ========================================================================");
        log.info("[VIDEO-QUALITY] Comparing: {}  vs  {}", file1Path, file2Path);
        log.info("[VIDEO-QUALITY] ------------------------------------------------------------------------");

        boolean result;
        String reason;
        String winnerPath;

        // Step 1: Date Comparison (Primary - oldest is original)
        log.info("[VIDEO-QUALITY] STEP 1 - Date Comparison (Primary Indicator)");
        log.info("[VIDEO-QUALITY]   Video 1: {}    Video 2: {}", video1DateStr, video2DateStr);

        if (video1Date != null && video2Date != null) {
            if (video1Date.before(video2Date)) {
                result = true;
                reason = "Video 1 is older (original by date)";
                winnerPath = file1Path;
                log.info("[VIDEO-QUALITY]   >> DECIDED: Video 1 wins (older date)");
            } else if (video1Date.after(video2Date)) {
                result = false;
                reason = "Video 2 is older (original by date)";
                winnerPath = file2Path;
                log.info("[VIDEO-QUALITY]   >> DECIDED: Video 2 wins (older date)");
            } else {
                log.info("[VIDEO-QUALITY]   >> TIE - same dates, checking next...");

                // Step 2: File Size (More important for videos than images)
                log.info("[VIDEO-QUALITY] STEP 2 - File Size (Important for video quality)");
                log.info("[VIDEO-QUALITY]   Video 1: {}    Video 2: {}", size1Str, size2Str);

                if (size1 != null && size2 != null) {
                    // For videos, larger file usually means better quality (higher bitrate)
                    // unless resolution is significantly different
                    if (size1 > size2) {
                        result = true;
                        reason = "Video 1 has larger file size (higher bitrate/quality)";
                        winnerPath = file1Path;
                        log.info("[VIDEO-QUALITY]   >> DECIDED: Video 1 wins (larger file)");
                    } else if (size2 > size1) {
                        result = false;
                        reason = "Video 2 has larger file size (higher bitrate/quality)";
                        winnerPath = file2Path;
                        log.info("[VIDEO-QUALITY]   >> DECIDED: Video 2 wins (larger file)");
                    } else {
                        log.info("[VIDEO-QUALITY]   >> TIE - same file size, checking next...");
                        result = checkResolutionAndPattern(video1, video2, width1, height1, width2, height2,
                                video1HasCopyPattern, video2HasCopyPattern, res1Str, res2Str,
                                file1Path, file2Path);
                        reason = getLastDecisionReason();
                        winnerPath = result ? file1Path : file2Path;
                    }
                } else {
                    result = true;
                    reason = "File size unavailable - defaulting to Video 1";
                    winnerPath = file1Path;
                    log.info("[VIDEO-QUALITY]   >> WARNING: File size unavailable, defaulting to Video 1");
                }
            }
        } else {
            // No date info - use file size and resolution
            log.info("[VIDEO-QUALITY]   >> Date unavailable for one or both videos");

            // Step 2: File Size
            log.info("[VIDEO-QUALITY] STEP 2 - File Size (Primary when no date)");
            log.info("[VIDEO-QUALITY]   Video 1: {}    Video 2: {}", size1Str, size2Str);

            if (size1 != null && size2 != null) {
                if (size1 > size2) {
                    result = true;
                    reason = "Video 1 has larger file size";
                    winnerPath = file1Path;
                    log.info("[VIDEO-QUALITY]   >> DECIDED: Video 1 wins (larger file)");
                } else if (size2 > size1) {
                    result = false;
                    reason = "Video 2 has larger file size";
                    winnerPath = file2Path;
                    log.info("[VIDEO-QUALITY]   >> DECIDED: Video 2 wins (larger file)");
                } else {
                    result = checkResolutionAndPattern(video1, video2, width1, height1, width2, height2,
                            video1HasCopyPattern, video2HasCopyPattern, res1Str, res2Str,
                            file1Path, file2Path);
                    reason = getLastDecisionReason();
                    winnerPath = result ? file1Path : file2Path;
                }
            } else {
                result = true;
                reason = "File size unavailable - defaulting to Video 1";
                winnerPath = file1Path;
                log.info("[VIDEO-QUALITY]   >> WARNING: File size unavailable, defaulting to Video 1");
            }
        }

        log.info("[VIDEO-QUALITY] ------------------------------------------------------------------------");
        log.info("[VIDEO-QUALITY]  WINNER: {}", winnerPath);
        log.info("[VIDEO-QUALITY]  REASON: {}", reason);
        log.info("[VIDEO-QUALITY] ========================================================================");

        return result;
    }

    private String lastDecisionReason = "";

    private boolean checkResolutionAndPattern(ExifData video1, ExifData video2,
            Integer width1, Integer height1,
            Integer width2, Integer height2,
            boolean video1HasCopyPattern,
            boolean video2HasCopyPattern,
            String res1Str, String res2Str,
            String file1Path, String file2Path) {
        // Step 3: Resolution
        log.info("[VIDEO-QUALITY] STEP 3 - Resolution");
        log.info("[VIDEO-QUALITY]   Video 1: {}    Video 2: {}", res1Str, res2Str);

        if (width1 != null && height1 != null && width2 != null && height2 != null) {
            long pixels1 = (long) width1 * height1;
            long pixels2 = (long) width2 * height2;

            if (pixels1 > pixels2) {
                lastDecisionReason = "Video 1 has higher resolution";
                log.info("[VIDEO-QUALITY]   >> DECIDED: Video 1 wins (higher resolution)");
                return true;
            } else if (pixels2 > pixels1) {
                lastDecisionReason = "Video 2 has higher resolution";
                log.info("[VIDEO-QUALITY]   >> DECIDED: Video 2 wins (higher resolution)");
                return false;
            }
        }

        log.info("[VIDEO-QUALITY]   >> TIE - same or unknown resolution, checking next...");

        // Step 4: Copy Pattern (Final Tiebreaker)
        log.info("[VIDEO-QUALITY] STEP 4 - Copy Pattern (Final Tiebreaker)");
        log.info("[VIDEO-QUALITY]   Video 1: {}    Video 2: {}", video1HasCopyPattern, video2HasCopyPattern);

        if (video1HasCopyPattern && !video2HasCopyPattern) {
            lastDecisionReason = "Video 2 has cleaner filename (no copy pattern)";
            log.info("[VIDEO-QUALITY]   >> DECIDED: Video 2 wins (no copy pattern)");
            return false;
        } else if (!video1HasCopyPattern && video2HasCopyPattern) {
            lastDecisionReason = "Video 1 has cleaner filename (no copy pattern)";
            log.info("[VIDEO-QUALITY]   >> DECIDED: Video 1 wins (no copy pattern)");
            return true;
        } else {
            lastDecisionReason = "Videos are equivalent - defaulting to Video 1";
            log.info("[VIDEO-QUALITY]   >> COMPLETE TIE - defaulting to Video 1");
            return true;
        }
    }

    private String getLastDecisionReason() {
        return lastDecisionReason;
    }

    private String formatFileSize(Long bytes) {
        if (bytes == null)
            return "N/A";
        if (bytes < 1024)
            return bytes + " B";
        if (bytes < 1024 * 1024)
            return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024)
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }
}
