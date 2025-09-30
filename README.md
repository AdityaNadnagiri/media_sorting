# Media Sorting Spring Boot Application

A Spring Boot application for organizing and sorting media files (images and videos) based on their EXIF data and metadata.

## Features

- **Automatic Media Organization**: Sorts photos and videos into organized folder structures based on date taken
- **Duplicate Detection**: Identifies and handles duplicate files using SHA-256 hashing
- **EXIF Data Extraction**: Reads metadata from images and videos including device information, location data, and timestamps
- **Device-based Organization**: Creates subfolders based on the device that captured the media
- **Safe Empty Folder Cleanup**: Intelligent removal of empty folders with comprehensive safety checks
  - Cross-platform hidden file detection (Windows, macOS, Linux)
  - System file and folder preservation  
  - DOS attribute checking on Windows
  - Important directory protection (.git, .svn, etc.)
- **REST API**: Provides web endpoints for triggering media organization
- **Comprehensive Logging**: Detailed logging with progress tracking and error reporting

## Technology Stack

- **Spring Boot 3.2.0**
- **Java 17**
- **Maven** for dependency management
- **metadata-extractor** for EXIF data reading
- **Apache Tika** for video metadata extraction
- **SLF4J** for logging

## Project Structure

```
src/
├── main/
│   ├── java/
│   │   └── com/media/sort/
│   │       ├── MediaSortingApplication.java          # Main Spring Boot application
│   │       ├── MediaSortingProperties.java           # Configuration properties
│   │       ├── controller/
│   │       │   └── MediaSortingController.java       # REST API endpoints
│   │       ├── model/
│   │       │   └── ExifData.java                     # Media file metadata model
│   │       ├── runner/
│   │       │   └── MediaSortingRunner.java           # Command line runner
│   │       └── service/
│   │           ├── FileExtensionAnalysisService.java # File extension analysis
│   │           ├── MediaFileService.java             # File processing operations
│   │           ├── PhotoOrganizerService.java        # Main organization logic
│   │           ├── ProgressTracker.java              # Progress tracking and logging
│   │           └── VideoExifDataService.java         # Video metadata extraction
│   └── resources/
│       └── application.properties                    # Application configuration
└── test/
    └── java/
        └── com/media/sort/
            └── MediaSortingApplicationTests.java     # Unit tests
```

## Configuration

The application can be configured through `application.properties`:

```properties
# Source folder for media files
app.media-sorting.source-folder=E:\\Marriage\\Engagement

# Logs folder
app.media-sorting.logs-folder=logs

# Create device-specific folders
app.media-sorting.create-device-folders=true

# Move duplicate files
app.media-sorting.move-duplicates=true

# Batch Job Configuration for Folder Comparison
app.media-sorting.batch-job.folder1-path=E:\\Photos\\Images
app.media-sorting.batch-job.folder2-path=E:\\Marriage
app.media-sorting.batch-job.thread-pool-size=20
app.media-sorting.batch-job.compare-logs-path=logs/compare
```

### Environment Variables
You can override any configuration using environment variables:
- `MEDIA_SOURCE_FOLDER`: Override the default source folder
- `LOGS_FOLDER`: Override the default logs folder
- `BATCH_FOLDER1_PATH`: Source folder for batch comparison job
- `BATCH_FOLDER2_PATH`: Reference folder for batch comparison job
- `BATCH_THREAD_POOL_SIZE`: Number of threads for parallel processing
- `BATCH_LOGS_PATH`: Directory for batch job logs

## Building and Running

### Prerequisites
- Java 17 or higher
- Maven 3.6 or higher

### Build the application
```bash
mvn clean compile
```

### Run the application
```bash
mvn spring-boot:run
```

### Build JAR file
```bash
mvn clean package
java -jar target/media-sorting-1.0.0-SNAPSHOT.jar
```

## Usage

### Command Line
The application automatically starts processing when run, using the configured source folder.

### REST API

#### Start Organization
```bash
POST /api/media/organize
```
Optional parameter: `sourceFolder` to override the default source folder.

#### Clean Up Empty Folders
```bash
POST /api/media/cleanup
```
Optional parameter: `targetFolder` to override the default source folder.
Features comprehensive safety checks for hidden files and system folders.

#### Compare and Move Duplicate Files (Batch Job)
```bash
POST /api/media/compare-folders
```
Optional parameters: 
- `folder1Path`: Source folder to process (defaults to configured batch job folder1 path)
- `folder2Path`: Reference folder to compare against (defaults to configured batch job folder2 path)

Compares files between two folders using SHA-256 hash comparison and moves duplicates from folder1 to organized structures based on folder2's directory layout.

#### Check Status
```bash
GET /api/media/status
```
Returns application status and configuration.

### Example API Usage
```bash
# Organize media using default folder
curl -X POST http://localhost:8080/api/media/organize

# Organize media from specific folder
curl -X POST "http://localhost:8080/api/media/organize?sourceFolder=C:\\Photos"

# Clean up empty folders using default folder
curl -X POST http://localhost:8080/api/media/cleanup

# Clean up empty folders from specific folder
curl -X POST "http://localhost:8080/api/media/cleanup?targetFolder=C:\\Photos"

# Run folder comparison batch job using default folders
curl -X POST http://localhost:8080/api/media/compare-folders

# Run folder comparison with custom folders
curl -X POST "http://localhost:8080/api/media/compare-folders?folder1Path=C:\\Photos\\Images&folder2Path=C:\\Marriage"

# Check application status
curl http://localhost:8080/api/media/status
```

## Output Structure

The application organizes files into the following structure:

```
[Source Folder]/
├── Images/
│   ├── Original/
│   │   ├── 2023-12-01/
│   │   │   ├── [Device Model]/
│   │   │   │   └── photo1.jpg
│   │   │   └── photo2.jpg
│   │   └── 2023-12-02/
│   │       └── photo3.jpg
│   └── Duplicate/
│       └── 2023-12-01/
│           └── duplicate_photo.jpg
├── Videos/
│   ├── Original/
│   │   └── 2023-12-01/
│   │       └── video1.mp4
│   └── Duplicate/
│       └── 2023-12-01/
│           └── duplicate_video.mp4
├── others/
│   └── unknown_file.txt
└── EmptyFolder/
    └── empty_directory/
```

## Supported File Types

### Images
- ARW, JPG, JPEG, GIF, BMP, ICO, TIF, TIFF, RAW, INDD, AI, EPS, PDF, HEIC, CR2, NRW, K25

### Videos  
- MP4, MKV, FLV, AVI, MOV, WMV, RM, MPG, MPEG, 3GP, VOB, M4V, 3G2, DIVX, XVID

## Logging

The application creates detailed logs in the configured logs folder:
- Progress tracking files
- Error logs for different processing stages
- File comparison logs for duplicates

## Migration from Original Project

This Spring Boot version includes all the functionality of the original Java application with these improvements:

1. **Spring Boot Framework**: Better dependency management and configuration
2. **REST API**: Web interface for remote operation
3. **Enhanced Logging**: Using SLF4J instead of System.out.println
4. **Service-Oriented Architecture**: Better separation of concerns
5. **Configuration Management**: External configuration support
6. **Error Handling**: Improved exception handling and logging

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests if applicable
5. Submit a pull request

## License

This project is licensed under the MIT License.