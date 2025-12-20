# Media Sorting Application - Complete Documentation

Last Updated: December 19, 2025

---

## Table of Contents
1. [Quick Start](#quick-start)
2. [Project Overview](#project-overview)
3. [Recent Critical Bug Fixes](#recent-critical-bug-fixes)
4. [Architecture & Features](#architecture--features)
5. [How to Run](#how-to-run)
6. [Build & Test Status](#build--test-status)
7. [Development History](#development-history)

---

## Quick Start

### Prerequisites
- Java 21+
- Maven 3.8+

### Run the Application
```bash
# Organize media files
mvn spring-boot:run "-Dspring-boot.run.arguments=--job=organize"

# Compare two folders for duplicates
mvn spring-boot:run "-Dspring-boot.run.arguments=--job=compare"
```

### Configure Source Folder
Edit `src/main/resources/application.properties`:
```properties
app.media-sorting.source-folder=G:\\photos\\Process\\Images
```

---

## Project Overview

A Spring Batch application for organizing photos and videos by automatically:
- Extracting metadata (date, camera model, resolution)
- Detecting duplicates (exact hash, perceptual similarity, filename patterns)
- Organizing into folder hierarchy: `Year-Month-Day/CameraModel/Extension/`
- Moving duplicates to separate folder with quality comparison

### Technology Stack
- **Java 21** - Modern language features (records, switch expressions)
- **Spring Batch** - Batch processing framework
- **Lombok** - Boilerplate reduction
- **Metadata Extraction**: `metadata-extractor`, Apache Tika, ImageIO
- **Perceptual Hashing**: JImageHash (optional)

---

## Recent Critical Bug Fixes

### December 19, 2025 - Critical Duplicate Detection Fixes

#### Bug #1: False Duplicate Detection (Filename Conflict)
**Problem**: Different files with same filename were treated as duplicates
- Example: Two different `UDLY5401.MOV` videos in same folder → One moved to Duplicates

**Fix**: Added SHA-256 hash comparison before quality check
- Different hashes → Add number suffix: `UDLY5401 (2).MOV`
- Same hash → True duplicate → Compare quality

**File**: `MediaFileService.java` (lines 165-256)

#### Bug #2: Incorrect Filename Pattern Matching
**Problem**: Short filenames matched unrelated longer filenames
- ❌ `1.jpg` matched `100_9517.jpg` (suffix "00_9517" matched pattern)
- ❌ `IMG.jpg` matched `IMG_1234.jpg`
- **Result**: Hundreds of files incorrectly moved to Duplicates!

**Fix**: Suffix must start with delimiter (space, hyphen, underscore, parenthesis)
- ✅ `1.jpg` → `1 - low.jpg` (valid)
- ✅ `1.jpg` → `1(2).jpg` (valid)
- ❌ `1.jpg` → `100_9517.jpg` (rejected - no delimiter)

**File**: `MediaFileWriter.java` (lines 499-522)

#### Logging Improvements
- Changed QuickTime/MP4 extraction errors from ERROR → DEBUG (expected fallbacks)
- Changed summary logs from INFO → DEBUG to reduce noise

**Files**: `VideoMetadataService.java`, `ImageMetadataService.java`, `ReportingService.java`

---

## Architecture & Features

### Core Services

#### **Metadata Extraction**
- `ImageMetadataService` - EXIF data from images
- `VideoMetadataService` - QuickTime, MP4, 3GP, AVI metadata
- Fallback chain: Specific format → Tika → Filesystem dates

#### **Duplicate Detection**
Three methods (in order):
1. **Exact Hash Match** (SHA-256) - All files
2. **Filename Pattern** - Copy suffixes: ` - low`, `(2)`, `_copy`
3. **Perceptual Hash** - Visually similar images (optional, configurable)

#### **Quality Comparison**
Priority order:
1. **Date** - Older = original (primary indicator)
2. **Resolution + File Size** - Both higher overrides date
3. **Copy Pattern** - Files with copy suffix are lower priority
4. **Burst Detection** - Sequential filenames kept as unique

**File**: `FileQualityComparator.java`

### Folder Organization

```
DestinationFolder/
├── Images/
│   ├── Original/
│   │   └── YYYY-MM-DD/
│   │       └── CameraModel/  (optional, if metadata available)
│   │           └── extension/  (jpg, png, etc.)
│   │               └── files
│   └── Duplicate/
│       └── [same structure]
└── Videos/
    ├── Original/
    └── Duplicate/
```

**Skip date folder** if metadata unavailable (graceful degradation)

**File**: `FolderPatternResolver.java`

### Batch Processing

- **Reader**: Scans source folder, calculates hashes
- **Processor**: Extracts metadata, checks duplicates
- **Writer**: Moves files to organized structure
- **Checkpointing**: Resume from failure
- **Transaction Logging**: Undo capability

---

## How to Run

### Job: Organize Media Files

```bash
mvn spring-boot:run "-Dspring-boot.run.arguments=--job=organize"
```

**What it does**:
1. Scans source folder (from `application.properties`)
2. Calculates file hashes
3. Extracts metadata (date, camera, resolution)
4. Detects duplicates (hash, pattern, perceptual)
5. Organizes into folder hierarchy
6. Moves duplicates to separate folder

### Job: Compare Folders

```bash
mvn spring-boot:run "-Dspring-boot.run.arguments=--job=compare"
```

**What it does**:
1. Compares two folders for duplicate files
2. Generates detailed comparison report
3. No files are moved

### Configuration

`application.properties`:
```properties
# Source folder to organize
app.media-sorting.source-folder=G:\\photos\\Process\\Images

# Enable/disable perceptual hash (resource intensive)
app.media-sorting.perceptual-hash-enabled=true

# Directory structure
app.directory-structure.images-directory-name=Images
app.directory-structure.videos-directory-name=Videos
app.directory-structure.original-sub-directory-name=Original
app.directory-structure.duplicate-sub-directory-name=Duplicate

# Supported extensions
app.media-sorting.image-extensions=jpg,jpeg,png,gif,bmp,tiff,webp
app.media-sorting.video-extensions=mp4,mov,avi,mkv,wmv,flv,3gp
```

---

## Build & Test Status

### Current Status: ✅ 100% SUCCESS

```bash
# Build with tests
mvn clean package

# Results:
Tests run: 48, Failures: 0, Errors: 0, Skipped: 0
```

### Test Coverage
- ✅ `FileQualityComparatorTest` - Duplicate comparison logic
- ✅ `DuplicatePatternUtilsTest` - Filename suffix detection
- ✅ `FolderPatternResolverTest` - Folder hierarchy generation
- ✅ `CheckpointServiceTest` - Batch job checkpointing
- ✅ `ReportingServiceTest` - Statistics and reporting
- ✅ All other unit tests passing

### Code Quality
- **Java 21** modern features (records, pattern matching, switch expressions)
- **Lombok** annotations for reduced boilerplate
- **Comprehensive logging** with SLF4J
- **Exception handling** with graceful fallbacks
- **Factory pattern** for dependency injection

---

## Development History

### Java 21 Modernization (Dec 2025)
- Converted POJOs to Records where applicable
- Replaced switch statements with switch expressions
- Added Lombok annotations (`@Data`, `@Slf4j`, `@RequiredArgsConstructor`)
- Removed boilerplate getters/setters/constructors

**Files**: `ExifData`, `MediaFileDTO`, `FileComparisonResult`

### Architecture Refactoring (Dec 2025)
**Separated image/video logic** into dedicated services:
- Created `ImageMetadataService` and `VideoMetadataService`
- Created `ExifDataFactory` for dependency injection
- Refactored `ExifData` into pure data model
- Updated all processors and services to use factory pattern

**Benefits**:
- Clear service boundaries
- Improved testability
- Easier to maintain and extend

### Duplicate Detection Refinement (Dec 2025)
**Enhancements**:
- Relaxed date check for explicit copy patterns
- Added burst shot protection (sequential filenames)
- Fixed image dimension extraction (ImageIO.read)
- Improved quality comparison logging

### Folder Hierarchy Fix (Dec 2025)
**Changes**:
- Extension folder always present (final subfolder)
- Device folder optional (skipped if no metadata)
- Date folder optional (skipped if no metadata available)
- Created `DuplicatePatternUtils` shared utility

### Logging Improvements (Dec 2025)
**Fixed encoding issues**:
- Removed emoji characters from logs (Windows incompatibility)
- Adjusted log levels (ERROR → DEBUG for expected fallbacks)
- Improved console output formatting

---

## Known Issues & Limitations

### Metadata Extraction
- **QuickTime/MP4 extraction** sometimes fails → Falls back to Tika/filesystem dates
- **3GP files** have limited metadata support
- **Filesystem dates** used as last resort (may be inaccurate)

### Perceptual Hashing
- **Resource intensive** - Can be disabled in config
- **Image-only** - No video perceptual hashing
- **Tunable threshold** - May need adjustment for your use case

### Duplicate Detection
- **Different encodings** of same video = different hashes (not detected as duplicates)
- **Resized images** with same visual content = different hashes (but perceptual hash can catch these)

---

## Future Enhancements

### Potential Improvements
1. **Video perceptual hashing** - Detect re-encoded videos
2. **Metadata-based video comparison** - Duration + dimensions
3. **GUI/Web interface** - Visual file management
4. **Batch undo** - Revert entire organization job
5. **Cloud storage support** - Google Photos, Dropbox, etc.

---

## File Reference

### Key Java Files

**Services**:
- `ImageMetadataService.java` - Image metadata extraction
- `VideoMetadataService.java` - Video metadata extraction
- `MediaFileService.java` - File operations and conflict resolution
- `PhotoOrganizerService.java` - Main organization orchestration
- `PerceptualHashService.java` - Visual similarity detection
- `ReportingService.java` - Statistics and reporting

**Batch Components**:
- `MediaFileReader.java` - Scans and reads source files
- `MediaFileProcessor.java` - Hash calculation and metadata extraction
- `FileHashProcessor.java` - Duplicate detection by hash
- `DuplicateFileProcessor.java` - Folder comparison duplicates
- `MediaFileWriter.java` - Moves files to organized structure

**Models**:
- `ExifData.java` - Metadata data model
- `MediaFileDTO.java` - Batch processing DTO (record)
- `FileComparisonResult.java` - Comparison result (record)

**Utilities**:
- `DuplicatePatternUtils.java` - Filename pattern detection
- `FolderPatternResolver.java` - Folder hierarchy logic
- `FileQualityComparator.java` - Duplicate quality comparison
- `FileOperationUtils.java` - File I/O utilities

**Configuration**:
- `MediaOrganizationJobConfig.java` - Organize job beans
- `FolderComparisonJobConfig.java` - Compare job beans
- `ExifDataFactory.java` - Factory for ExifData creation

---

## Support & Contact

For issues or questions:
1. Check this documentation
2. Review recent bug fixes section
3. Check test results: `mvn clean test`
4. Review logs in `logs/` directory

---

**Status**: Production Ready ✅
**Last Tested**: December 19, 2025
**Build**: Passing (48/48 tests)
**Critical Bugs**: Fixed (Hash comparison, Pattern matching)