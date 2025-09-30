package com.media.sort.controller;

import com.media.sort.MediaSortingProperties;
import com.media.sort.service.PhotoOrganizerService;
import com.media.sort.service.EmptyFolderCleanupService;
import com.media.sort.service.FolderComparisonService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/media")
public class MediaSortingController {

    private static final Logger logger = LoggerFactory.getLogger(MediaSortingController.class);

    @Autowired
    private PhotoOrganizerService photoOrganizerService;

    @Autowired
    private MediaSortingProperties properties;
    
    @Autowired
    private EmptyFolderCleanupService emptyFolderCleanupService;
    
    @Autowired
    private FolderComparisonService folderComparisonService;

    @PostMapping("/organize")
    public ResponseEntity<Map<String, String>> organizeMedia(@RequestParam(required = false) String sourceFolder) {
        Map<String, String> response = new HashMap<>();
        
        try {
            String folderToOrganize = sourceFolder != null ? sourceFolder : properties.getSourceFolder();
            logger.info("Starting media organization for folder: {}", folderToOrganize);
            
            photoOrganizerService.organizePhotos(folderToOrganize);
            
            response.put("status", "success");
            response.put("message", "Media organization completed successfully");
            response.put("sourceFolder", folderToOrganize);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error during media organization", e);
            response.put("status", "error");
            response.put("message", "Error during media organization: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("applicationName", "Media Sorting Application");
        status.put("version", "1.0.0");
        status.put("defaultSourceFolder", properties.getSourceFolder());
        status.put("logsFolder", properties.getLogsFolder());
        status.put("createDeviceFolders", properties.isCreateDeviceFolders());
        status.put("moveDuplicates", properties.isMoveDuplicates());
        
        return ResponseEntity.ok(status);
    }

    @PostMapping("/cleanup")
    public ResponseEntity<Map<String, Object>> cleanupEmptyFolders(@RequestParam(required = false) String targetFolder) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            String folderToCleanup = targetFolder != null ? targetFolder : properties.getSourceFolder();
            logger.info("Starting empty folder cleanup for: {}", folderToCleanup);
            
            EmptyFolderCleanupService.CleanupResult result = emptyFolderCleanupService.deleteEmptyFolders(folderToCleanup);
            int deletedCount = result.getFoldersDeleted();
            
            response.put("status", "success");
            response.put("message", "Empty folder cleanup completed successfully");
            response.put("deletedFolders", deletedCount);
            response.put("processedPath", folderToCleanup);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error during empty folder cleanup", e);
            response.put("status", "error");
            response.put("message", "Error occurred during cleanup: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @PostMapping("/compare-folders")
    public ResponseEntity<Map<String, Object>> compareFolders(
            @RequestParam(required = false) String folder1Path,
            @RequestParam(required = false) String folder2Path) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Temporarily override paths if provided
            MediaSortingProperties.BatchJobProperties originalBatchJob = null;
            if (folder1Path != null || folder2Path != null) {
                originalBatchJob = properties.getBatchJob();
                MediaSortingProperties.BatchJobProperties tempBatchJob = new MediaSortingProperties.BatchJobProperties();
                tempBatchJob.setFolder1Path(folder1Path != null ? folder1Path : originalBatchJob.getFolder1Path());
                tempBatchJob.setFolder2Path(folder2Path != null ? folder2Path : originalBatchJob.getFolder2Path());
                tempBatchJob.setThreadPoolSize(originalBatchJob.getThreadPoolSize());
                tempBatchJob.setCompareLogsPath(originalBatchJob.getCompareLogsPath());
                properties.setBatchJob(tempBatchJob);
            }

            logger.info("Starting folder comparison batch job");
            FolderComparisonService.ComparisonResult result = folderComparisonService.compareAndMoveFiles();
            
            response.put("status", result.getStatus());
            response.put("message", result.getMessage());
            response.put("folder1ProcessedFiles", result.getFolder1ProcessedFiles());
            response.put("folder2ProcessedFiles", result.getFolder2ProcessedFiles());
            response.put("movedFiles", result.getMovedFiles());
            response.put("folder1Path", properties.getBatchJob().getFolder1Path());
            response.put("folder2Path", properties.getBatchJob().getFolder2Path());
            
            // Restore original batch job properties if they were overridden
            if (originalBatchJob != null) {
                properties.setBatchJob(originalBatchJob);
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error during folder comparison batch job", e);
            response.put("status", "error");
            response.put("message", "Error occurred during folder comparison: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
}