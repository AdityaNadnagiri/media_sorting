# Media Sorting Batch Application - Complete Documentation

## Overview

The Media Sorting Batch Application is a comprehensive Spring Boot application designed to automatically organize, deduplicate, and manage media files (photos and videos). It provides both batch processing capabilities and REST API endpoints for media file organization.

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                  Media Sorting Application                   │
├─────────────────────────────────────────────────────────────┤
│  Entry Points:                                              │
│  • CommandLineRunner (Batch Processing)                     │
│  • REST Controller (API Endpoints)                          │
├─────────────────────────────────────────────────────────────┤
│  Core Services:                                             │
│  • PhotoOrganizerService (Main orchestrator)               │
│  • FolderComparisonService (Duplicate detection)           │
│  • MediaFileService (File operations)                      │
│  • EmptyFolderCleanupService (Cleanup operations)          │
├─────────────────────────────────────────────────────────────┤
│  Support Components:                                        │
│  • ExifData (Metadata extraction)                          │
│  • ProgressTracker (Logging & monitoring)                  │
│  • FileTypeUtils (File type detection)                     │
│  • FileOperationUtils (File system operations)             │
└─────────────────────────────────────────────────────────────┘
```

## Application Flow

### 1. **Application Startup**

```java
@SpringBootApplication
@EnableConfigurationProperties(MediaSortingProperties.class)
public class MediaSortingApplication {
    public static void main(String[] args) {
        SpringApplication.run(MediaSortingApplication.class, args);
    }
}
```

**Flow:**
1. Spring Boot application starts
2. Configuration properties loaded from `application.properties`
3. All services initialized with dependency injection
4. CommandLineRunner automatically executes batch process

### 2. **Batch Processing Flow (CommandLineRunner)**

```
Start Application
       ↓
MediaSortingRunner.run()
       ↓
Load Configuration Properties
       ↓
PhotoOrganizerService.organizePhotos()
       ↓
Process Source Directory
       ↓
Complete & Log Results
```

**Detailed Steps:**

#### **Step 1: MediaSortingRunner Initialization**
```java
@Component
public class MediaSortingRunner implements CommandLineRunner {
    @Override
    public void run(String... args) throws Exception {
        logger.info("Starting Media Sorting Application");
        logger.info("Source folder: {}", properties.getSourceFolder());
        
        photoOrganizerService.organizePhotos(properties.getSourceFolder());
        logger.info("Media sorting completed successfully!");
    }
}
```

#### **Step 2: Photo Organization Process**

**2.1 Directory Structure Creation:**
```
Source Folder/
├── Images/
│   ├── Original/       # Unique images organized by date
│   └── Duplicate/      # Duplicate images with conflict resolution
├── Videos/
│   ├── Original/       # Unique videos organized by date
│   └── Duplicate/      # Duplicate videos with conflict resolution
├── EmptyFolder/        # Empty directories moved here
└── others/             # Non-media files
```

**2.2 File Processing Logic:**
```java
for (File file : files) {
    if (file.isFile()) {
        ExifData fileData = new ExifData(file);  // Extract metadata
        if (!fileData.isOther()) {
            mediaFileService.processFile(fileData, this);  // Process media
        } else {
            mediaFileService.executeMove(fileData, new File(othersDirectory));  // Move others
        }
    } else if (file.isDirectory()) {
        // Recursive processing or empty folder handling
    }
}
```

### 3. **File Processing Pipeline**

#### **3.1 EXIF Data Extraction**
```java
public class ExifData {
    // Extracts metadata from images and videos:
    // - Creation date/time
    // - Camera information
    // - GPS coordinates
    // - File dimensions
    // - Device information
}
```

**Supported Formats:**
- **Images:** JPG, JPEG, PNG, GIF, BMP, TIFF, WEBP
- **Videos:** MP4, AVI, MOV, MKV, WMV, FLV, WEBM

#### **3.2 Duplicate Detection Algorithm**

```
File Input
    ↓
Calculate SHA-256 Hash
    ↓
Check Hash in fileHash Map
    ↓
┌─────────────────┬─────────────────┐
│   Hash Exists   │  Hash Not Found │
│   (Duplicate)   │     (Unique)    │
└─────────────────┴─────────────────┘
    ↓                       ↓
Compare Creation Dates   Add to Hash Map
    ↓                       ↓
Keep Newer File         Move to Original/
    ↓                   {YYYY-MM-DD}/
Move Older to              ↓
Duplicate/ Folder       Log Progress
```

**Hash-based Duplicate Detection:**
```java
public String calculateHash(String filePath) {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    // Read file in chunks and calculate hash
    // Returns hexadecimal string representation
}
```

#### **3.3 File Organization Strategy**

**Date-based Organization:**
```
Original/
├── 2023-01-15/
│   ├── IMG_001.jpg
│   └── VID_001.mp4
├── 2023-01-16/
│   └── IMG_002.jpg
└── Unknown-Date/
    └── file_without_date.jpg
```

**Duplicate Handling:**
```
Duplicate/
├── 2023-01-15_conflicts/
│   ├── IMG_001_duplicate_1.jpg
│   └── IMG_001_duplicate_2.jpg
└── resolution_log.txt
```

### 4. **REST API Endpoints**

The application provides REST endpoints for manual operations:

#### **4.1 Photo Organization**
```http
POST /api/media/organize
Content-Type: application/json

{
    "sourceFolder": "E:\\Photos\\MyAlbum"
}
```

#### **4.2 Folder Comparison**
```http
POST /api/media/compare-folders
Content-Type: application/json

{
    "folder1": "E:\\Photos\\Album1",
    "folder2": "E:\\Photos\\Album2"
}
```

#### **4.3 Empty Folder Cleanup**
```http
POST /api/media/cleanup-empty-folders
Content-Type: application/json

{
    "rootPath": "E:\\Photos"
}
```

### 5. **Folder Comparison Service (Batch Job)**

This service compares two large directories for duplicate files using multi-threading:

```java
public ComparisonResult compareAndMoveFiles() {
    // Step 1: Scan folder2 and build hash map
    buildFolder2HashMap();
    
    // Step 2: Process folder1 files in parallel
    ExecutorService executor = Executors.newFixedThreadPool(threadPoolSize);
    processFolder1Files(executor);
    
    // Step 3: Generate statistics and cleanup
    return generateComparisonResult();
}
```

**Multi-threaded Processing:**
```
Thread Pool (20 threads by default)
    ↓
┌─────────┬─────────┬─────────┬─────────┐
│Thread 1 │Thread 2 │Thread 3 │Thread N │
│Process  │Process  │Process  │Process  │
│File A   │File B   │File C   │File X   │
└─────────┴─────────┴─────────┴─────────┘
    ↓
Concurrent Hash Map Updates
    ↓
Progress Tracking & Logging
```

### 6. **Configuration Management**

#### **6.1 Application Properties**
```properties
# Source directory for batch processing
app.media-sorting.source-folder=E:\\Marriage\\Engagement

# Batch job configuration
app.media-sorting.batch-job.folder1-path=E:\\Photos\\Images
app.media-sorting.batch-job.folder2-path=E:\\Marriage
app.media-sorting.batch-job.thread-pool-size=20

# Feature flags
app.media-sorting.create-device-folders=true
app.media-sorting.move-duplicates=true
```

#### **6.2 Environment Variables**
```bash
# Override default paths
export MEDIA_SOURCE_FOLDER="D:\\MyPhotos"
export BATCH_FOLDER1_PATH="D:\\Photos\\Collection1"
export BATCH_FOLDER2_PATH="D:\\Photos\\Collection2"
export BATCH_THREAD_POOL_SIZE=30
```

### 7. **Progress Tracking & Logging**

#### **7.1 Progress Tracking System**
```java
public class ProgressTracker {
    // Tracks processing progress with timestamps
    // Logs to specific files for different operations
    // Thread-safe concurrent operations
}
```

**Log File Structure:**
```
logs/
├── po/                    # Photo Organizer logs
│   ├── error.txt         # Error tracking
│   ├── image/
│   │   └── error.txt     # Image processing errors
│   └── video/
│       └── error.txt     # Video processing errors
├── compare/               # Folder comparison logs
│   └── progress.txt      # Comparison progress
└── cleanup/
    └── empty-folders.txt  # Cleanup operations
```

#### **7.2 Logging Levels**
```properties
logging.level.com.media.sort=INFO
logging.pattern.console=%d{yyyy-MM-dd HH:mm:ss} - %msg%n
```

### 8. **Utility Components**

#### **8.1 FileTypeUtils**
```java
public class FileTypeUtils {
    public static boolean isImage(String filename);
    public static boolean isVideo(String filename);
    public static String getFileType(String filename);
    public static String getExtension(String filename);
}
```

#### **8.2 FileOperationUtils**
```java
public class FileOperationUtils {
    public static Path findUniqueFileName(Path path);
    public static boolean createDirectoryIfNotExists(Path directory);
    public static boolean safeMove(Path source, Path destination);
    public static boolean isDirectoryEmpty(Path directory);
}
```

### 9. **Error Handling & Recovery**

#### **9.1 Exception Handling Strategy**
```java
try {
    // File operation
} catch (IOException e) {
    logger.error("File operation failed: {}", filename, e);
    progressTracker.saveProgress("Error: " + filename + " - " + e.getMessage());
    // Continue processing other files
}
```

#### **9.2 Recovery Mechanisms**
- **Duplicate Name Resolution:** Automatic filename suffixing
- **Permission Errors:** Logged and skipped
- **Corrupt Files:** Moved to error directory
- **Progress Tracking:** Resume capability through logs

### 10. **Performance Optimizations**

#### **10.1 Multi-threading**
- **Thread Pool:** Configurable size (default: 20)
- **Concurrent Hash Maps:** Thread-safe duplicate detection
- **Parallel Stream Processing:** For large directory traversals

#### **10.2 Memory Management**
- **Streaming File Processing:** Avoid loading entire files in memory
- **Hash Calculation:** Chunk-based reading (8KB buffers)
- **Progress Tracking:** Periodic flushing to disk

#### **10.3 I/O Optimization**
- **NIO.2 API:** Modern file operations
- **Batch Directory Creation:** Reduce system calls
- **Metadata Caching:** Avoid repeated EXIF reading

## Usage Examples

### **Batch Mode (Default)**
```bash
# Run with default configuration
java -jar media-sorting-1.0.0-SNAPSHOT.jar

# Run with custom source folder
java -jar media-sorting-1.0.0-SNAPSHOT.jar --app.media-sorting.source-folder="D:\\MyPhotos"

# Run with environment variables
export MEDIA_SOURCE_FOLDER="D:\\Photos"
java -jar media-sorting-1.0.0-SNAPSHOT.jar
```

### **API Mode**
```bash
# Start application as service
java -jar media-sorting-1.0.0-SNAPSHOT.jar

# Make API calls
curl -X POST http://localhost:8080/api/media/organize \
  -H "Content-Type: application/json" \
  -d '{"sourceFolder": "D:\\Photos\\Vacation2023"}'
```

## Output Examples

### **Console Output**
```
2025-09-29 19:23:52 - Starting Media Sorting Application
2025-09-29 19:23:52 - Source folder: E:\Marriage\Engagement
2025-09-29 19:23:52 - Starting photo organization for folder: E:\Marriage\Engagement
2025-09-29 19:23:54 - Processed 1,234 files
2025-09-29 19:23:54 - Found 89 duplicates
2025-09-29 19:23:54 - Organized 1,145 unique files
2025-09-29 19:23:54 - Media sorting completed successfully!
```

### **API Response**
```json
{
  "status": "success",
  "message": "Organization completed successfully",
  "statistics": {
    "totalFiles": 1234,
    "uniqueFiles": 1145,
    "duplicates": 89,
    "errors": 0,
    "processingTimeMs": 12450
  }
}
```

## Key Features Summary

### ✅ **Automated Processing**
- Batch processing on application startup
- Recursive directory scanning
- Automatic file organization by date

### ✅ **Duplicate Detection**
- SHA-256 hash-based comparison
- Intelligent date-based conflict resolution
- Preserves newest versions

### ✅ **Multi-format Support**
- Images: JPG, PNG, GIF, BMP, TIFF, WEBP
- Videos: MP4, AVI, MOV, MKV, WMV, FLV
- EXIF/metadata extraction for both

### ✅ **Performance**
- Multi-threaded processing
- Configurable thread pool size
- Memory-efficient streaming

### ✅ **Monitoring**
- Comprehensive logging
- Progress tracking
- Error reporting and recovery

### ✅ **Flexibility**
- REST API endpoints
- Configuration-driven behavior
- Environment variable support

### ✅ **Reliability**
- Exception handling
- Safe file operations
- Atomic moves with rollback

This comprehensive batch application provides enterprise-grade media file organization with both automated batch processing and on-demand API capabilities.