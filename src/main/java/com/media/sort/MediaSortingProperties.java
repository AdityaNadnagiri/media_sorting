package com.media.sort;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.Set;

@ConfigurationProperties(prefix = "app.media-sorting")
public class MediaSortingProperties {
    
    // Main source folder for media processing
    private String sourceFolder;
    
    // Root logs directory
    private String rootLogsFolder;
    
    // Feature flags
    private boolean enableDeviceFolderCreation;
    private boolean enableDuplicateMoving;
    
    // Directory structure configuration
    private DirectoryStructure directoryStructure = new DirectoryStructure();
    
    // Log file paths configuration
    private LogFilePaths logFilePaths = new LogFilePaths();
    
    // File type extensions configuration
    private FileExtensions fileExtensions = new FileExtensions();
    
    // Batch job properties for folder comparison
    private BatchJobProperties batchJob = new BatchJobProperties();
    
    public BatchJobProperties getBatchJob() {
        return batchJob;
    }
    
    public void setBatchJob(BatchJobProperties batchJob) {
        this.batchJob = batchJob;
    }
    
    public static class DirectoryStructure {
        private String emptyFolderDirectoryName;
        private String imagesDirectoryName;
        private String videosDirectoryName;
        private String originalSubDirectoryName;
        private String duplicateSubDirectoryName;
        private String othersDirectoryName;
        
        // Getters and setters
        public String getEmptyFolderDirectoryName() { return emptyFolderDirectoryName; }
        public void setEmptyFolderDirectoryName(String emptyFolderDirectoryName) { this.emptyFolderDirectoryName = emptyFolderDirectoryName; }
        
        public String getImagesDirectoryName() { return imagesDirectoryName; }
        public void setImagesDirectoryName(String imagesDirectoryName) { this.imagesDirectoryName = imagesDirectoryName; }
        
        public String getVideosDirectoryName() { return videosDirectoryName; }
        public void setVideosDirectoryName(String videosDirectoryName) { this.videosDirectoryName = videosDirectoryName; }
        
        public String getOriginalSubDirectoryName() { return originalSubDirectoryName; }
        public void setOriginalSubDirectoryName(String originalSubDirectoryName) { this.originalSubDirectoryName = originalSubDirectoryName; }
        
        public String getDuplicateSubDirectoryName() { return duplicateSubDirectoryName; }
        public void setDuplicateSubDirectoryName(String duplicateSubDirectoryName) { this.duplicateSubDirectoryName = duplicateSubDirectoryName; }
        
        public String getOthersDirectoryName() { return othersDirectoryName; }
        public void setOthersDirectoryName(String othersDirectoryName) { this.othersDirectoryName = othersDirectoryName; }
    }
    
    public static class LogFilePaths {
        private String imageErrorLogPath;
        private String videoErrorLogPath;
        private String mediaErrorLogPath;
        private String photoOrganizerErrorLogPath;
        private String fileComparisonLogPath;
        private String emptyFolderCleanupLogPath;
        private String folderComparisonProgressLogPath;
        private String videoMp4ErrorLogPath;
        private String videoTgpErrorLogPath;
        private String videoQtErrorLogPath;
        private String videoOtherErrorLogPath;
        
        // Getters and setters
        public String getImageErrorLogPath() { return imageErrorLogPath; }
        public void setImageErrorLogPath(String imageErrorLogPath) { this.imageErrorLogPath = imageErrorLogPath; }
        
        public String getVideoErrorLogPath() { return videoErrorLogPath; }
        public void setVideoErrorLogPath(String videoErrorLogPath) { this.videoErrorLogPath = videoErrorLogPath; }
        
        public String getMediaErrorLogPath() { return mediaErrorLogPath; }
        public void setMediaErrorLogPath(String mediaErrorLogPath) { this.mediaErrorLogPath = mediaErrorLogPath; }
        
        public String getPhotoOrganizerErrorLogPath() { return photoOrganizerErrorLogPath; }
        public void setPhotoOrganizerErrorLogPath(String photoOrganizerErrorLogPath) { this.photoOrganizerErrorLogPath = photoOrganizerErrorLogPath; }
        
        public String getFileComparisonLogPath() { return fileComparisonLogPath; }
        public void setFileComparisonLogPath(String fileComparisonLogPath) { this.fileComparisonLogPath = fileComparisonLogPath; }
        
        public String getEmptyFolderCleanupLogPath() { return emptyFolderCleanupLogPath; }
        public void setEmptyFolderCleanupLogPath(String emptyFolderCleanupLogPath) { this.emptyFolderCleanupLogPath = emptyFolderCleanupLogPath; }
        
        public String getFolderComparisonProgressLogPath() { return folderComparisonProgressLogPath; }
        public void setFolderComparisonProgressLogPath(String folderComparisonProgressLogPath) { this.folderComparisonProgressLogPath = folderComparisonProgressLogPath; }
        
        public String getVideoMp4ErrorLogPath() { return videoMp4ErrorLogPath; }
        public void setVideoMp4ErrorLogPath(String videoMp4ErrorLogPath) { this.videoMp4ErrorLogPath = videoMp4ErrorLogPath; }
        
        public String getVideoTgpErrorLogPath() { return videoTgpErrorLogPath; }
        public void setVideoTgpErrorLogPath(String videoTgpErrorLogPath) { this.videoTgpErrorLogPath = videoTgpErrorLogPath; }
        
        public String getVideoQtErrorLogPath() { return videoQtErrorLogPath; }
        public void setVideoQtErrorLogPath(String videoQtErrorLogPath) { this.videoQtErrorLogPath = videoQtErrorLogPath; }
        
        public String getVideoOtherErrorLogPath() { return videoOtherErrorLogPath; }
        public void setVideoOtherErrorLogPath(String videoOtherErrorLogPath) { this.videoOtherErrorLogPath = videoOtherErrorLogPath; }
    }
    
    public static class FileExtensions {
        private Set<String> supportedImageExtensions;
        private Set<String> supportedVideoExtensions;
        
        // Getters and setters
        public Set<String> getSupportedImageExtensions() { return supportedImageExtensions; }
        public void setSupportedImageExtensions(Set<String> supportedImageExtensions) { this.supportedImageExtensions = supportedImageExtensions; }
        
        public Set<String> getSupportedVideoExtensions() { return supportedVideoExtensions; }
        public void setSupportedVideoExtensions(Set<String> supportedVideoExtensions) { this.supportedVideoExtensions = supportedVideoExtensions; }
    }
    
    public static class BatchJobProperties {
        private String primaryFolderPath;
        private String secondaryFolderPath;
        private int maxThreadPoolSize;
        private String comparisonLogsDirectoryPath;
        
        public String getPrimaryFolderPath() {
            return primaryFolderPath;
        }
        
        public void setPrimaryFolderPath(String primaryFolderPath) {
            this.primaryFolderPath = primaryFolderPath;
        }
        
        public String getSecondaryFolderPath() {
            return secondaryFolderPath;
        }
        
        public void setSecondaryFolderPath(String secondaryFolderPath) {
            this.secondaryFolderPath = secondaryFolderPath;
        }
        
        public int getMaxThreadPoolSize() {
            return maxThreadPoolSize;
        }
        
        public void setMaxThreadPoolSize(int maxThreadPoolSize) {
            this.maxThreadPoolSize = maxThreadPoolSize;
        }
        
        public String getComparisonLogsDirectoryPath() {
            return comparisonLogsDirectoryPath;
        }
        
        public void setComparisonLogsDirectoryPath(String comparisonLogsDirectoryPath) {
            this.comparisonLogsDirectoryPath = comparisonLogsDirectoryPath;
        }
    }
    
    public String getSourceFolder() {
        return sourceFolder;
    }
    
    public void setSourceFolder(String sourceFolder) {
        this.sourceFolder = sourceFolder;
    }
    
    public String getRootLogsFolder() {
        return rootLogsFolder;
    }
    
    public void setRootLogsFolder(String rootLogsFolder) {
        this.rootLogsFolder = rootLogsFolder;
    }
    
    public boolean isEnableDeviceFolderCreation() {
        return enableDeviceFolderCreation;
    }
    
    public void setEnableDeviceFolderCreation(boolean enableDeviceFolderCreation) {
        this.enableDeviceFolderCreation = enableDeviceFolderCreation;
    }
    
    public boolean isEnableDuplicateMoving() {
        return enableDuplicateMoving;
    }
    
    public void setEnableDuplicateMoving(boolean enableDuplicateMoving) {
        this.enableDuplicateMoving = enableDuplicateMoving;
    }
    
    public DirectoryStructure getDirectoryStructure() {
        return directoryStructure;
    }
    
    public void setDirectoryStructure(DirectoryStructure directoryStructure) {
        this.directoryStructure = directoryStructure;
    }
    
    public LogFilePaths getLogFilePaths() {
        return logFilePaths;
    }
    
    public void setLogFilePaths(LogFilePaths logFilePaths) {
        this.logFilePaths = logFilePaths;
    }
    
    public FileExtensions getFileExtensions() {
        return fileExtensions;
    }
    
    public void setFileExtensions(FileExtensions fileExtensions) {
        this.fileExtensions = fileExtensions;
    }
}