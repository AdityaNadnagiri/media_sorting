package com.media.sort.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarBuilder;
import me.tongfei.progressbar.ProgressBarStyle;

/**
 * Service for displaying progress bars during batch processing
 */
@Service
public class ProgressBarService {

    @Value("${media.progress.enabled:true}")
    private boolean enabled;

    @Value("${media.progress.style:ASCII}")
    private String style;

    private ProgressBar currentProgressBar;

    /**
     * Start a new progress bar
     */
    public ProgressBar startProgress(String taskName, long totalItems) {
        if (!enabled) {
            return null;
        }

        ProgressBarStyle barStyle = ProgressBarStyle.valueOf(style);

        currentProgressBar = new ProgressBarBuilder()
                .setTaskName(taskName)
                .setInitialMax(totalItems)
                .setStyle(barStyle)
                .setUpdateIntervalMillis(100)
                .build();

        return currentProgressBar;
    }

    /**
     * Update progress
     */
    public void step() {
        if (currentProgressBar != null) {
            currentProgressBar.step();
        }
    }

    /**
     * Update progress with message
     */
    public void stepTo(long current, String message) {
        if (currentProgressBar != null) {
            currentProgressBar.stepTo(current);
            currentProgressBar.setExtraMessage(message);
        }
    }

    /**
     * Complete and close progress bar
     */
    public void complete() {
        if (currentProgressBar != null) {
            currentProgressBar.close();
            currentProgressBar = null;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }
}
