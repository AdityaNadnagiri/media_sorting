package com.media.sort;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.media-sorting")
public class MediaSortingProperties {
    
    private String sourceFolder = "E:\\Marriage\\Engagement";
    private String logsFolder = "logs";
    private boolean createDeviceFolders = true;
    private boolean moveDuplicates = true;
    
    // Batch job properties for folder comparison
    private BatchJobProperties batchJob = new BatchJobProperties();
    
    public BatchJobProperties getBatchJob() {
        return batchJob;
    }
    
    public void setBatchJob(BatchJobProperties batchJob) {
        this.batchJob = batchJob;
    }
    
    public static class BatchJobProperties {
        private String folder1Path = "E:\\Photos\\Images";
        private String folder2Path = "E:\\Marriage";
        private int threadPoolSize = 20;
        private String compareLogsPath = "logs/compare";
        
        public String getFolder1Path() {
            return folder1Path;
        }
        
        public void setFolder1Path(String folder1Path) {
            this.folder1Path = folder1Path;
        }
        
        public String getFolder2Path() {
            return folder2Path;
        }
        
        public void setFolder2Path(String folder2Path) {
            this.folder2Path = folder2Path;
        }
        
        public int getThreadPoolSize() {
            return threadPoolSize;
        }
        
        public void setThreadPoolSize(int threadPoolSize) {
            this.threadPoolSize = threadPoolSize;
        }
        
        public String getCompareLogsPath() {
            return compareLogsPath;
        }
        
        public void setCompareLogsPath(String compareLogsPath) {
            this.compareLogsPath = compareLogsPath;
        }
    }
    
    public String getSourceFolder() {
        return sourceFolder;
    }
    
    public void setSourceFolder(String sourceFolder) {
        this.sourceFolder = sourceFolder;
    }
    
    public String getLogsFolder() {
        return logsFolder;
    }
    
    public void setLogsFolder(String logsFolder) {
        this.logsFolder = logsFolder;
    }
    
    public boolean isCreateDeviceFolders() {
        return createDeviceFolders;
    }
    
    public void setCreateDeviceFolders(boolean createDeviceFolders) {
        this.createDeviceFolders = createDeviceFolders;
    }
    
    public boolean isMoveDuplicates() {
        return moveDuplicates;
    }
    
    public void setMoveDuplicates(boolean moveDuplicates) {
        this.moveDuplicates = moveDuplicates;
    }
}