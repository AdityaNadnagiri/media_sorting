# Media Sorting Application

**Version:** 1.0.0-SNAPSHOT  
**Status:** Production Ready ✅  
**Last Updated:** 2025-12-14

---

## 📋 Overview

Intelligent media file organizer that sorts photos and videos by date and device, with advanced duplicate detection using both SHA-256 hashing and perceptual hashing for visual similarity.

### Key Features

- ✅ **Exact Duplicate Detection** - SHA-256 hash comparison
- ✅ **Perceptual Duplicate Detection** - DCT-based image similarity (different resolutions)
- ✅ **Smart Quality Ranking** - Prioritizes clean filenames, older dates, higher resolution
- ✅ **Automatic Filename Cleaning** - Removes OS duplicate patterns from originals
- ✅ **Thread-Safe Processing** - ConcurrentHashMap for parallel operations
- ✅ **Empty Folder Cleanup** - Automatic recursive cleanup
- ✅ **Unique Log Directories** - Per-run logging with console output capture

---

## 🚀 Quick Start

### Run Organization Job
```powershell
java -jar target/media-sorting-1.0.0-SNAPSHOT.jar --job=organize --sourceFolder="D:\Photos"
```

### Build from Source
```powershell
mvn clean package -DskipTests
```

---

## 📊 How It Works

### Duplicate Detection Flow

```
1. Calculate SHA-256 hash (exact content match)
   ├─ Match found? → Exact duplicate
   │  ├─ Compare using isBetterQualityThan()
   │  └─ Move: Better → Original/, Worse → Duplicate/
   │
   └─ No match? → Check perceptual hash (images only)
      ├─ Perceptually similar?
      │  ├─ Compare using isBetterQualityThan()
      │  └─ Move: Better → Original/, Worse → Duplicate/
      │
      └─ Not similar? → Unique file
         └─ Move to Original/ (clean filename)
```

### Quality Comparison Priority

```
1. OS Duplicate Pattern Check
   ├─ File WITHOUT " - Copy", "(1)", etc. = BETTER
   └─ File WITH patterns = WORSE
   
2. Date Comparison (if both clean or both have patterns)
   ├─ Older date = BETTER (true original)
   └─ Newer date = WORSE (likely a copy/edit)
   
3. Resolution Comparison (if dates equal/unknown)
   ├─ Higher resolution (width × height) = BETTER
   └─ Lower resolution = WORSE
   
4. File Size (fallback)
   ├─ Larger file = BETTER
   └─ Smaller file = WORSE
```

### OS Duplicate Patterns Detected

Files with these patterns are deprioritized:
- `(1)`, `(2)`, `(123)` - Numbering
- ` - Copy`, ` - Copy (2)` - Windows style
- ` copy 1`, `copy1`, `_copy_1` - Various copy formats
- `1copy1`, `2copy2` - Numbered copies

**Original Folder:** Patterns are removed (e.g., `Photo - Copy.JPG` → `Photo.JPG`)  
**Duplicate Folder:** Files get numbered (e.g., `Photo(1).JPG`, `Photo(2).JPG`)

---

## 📁 Directory Structure

### Before Processing
```
SourceFolder/
├── IMG_001.jpg
├── IMG_001 - Copy.jpg
├── IMG_002.jpg (1920×1080)
├── IMG_002_low.jpg (800×600)
└── random_folder/
    └── video.mp4
```

### After Processing
```
SourceFolder/
├── Images/
│   ├── Original/
│   │   └── 2024-01-15/
│   │       └── iPhone 12/
│   │           ├── IMG_001.jpg (cleaned name)
│   │           └── IMG_002.jpg (high-res)
│   └── Duplicate/
│       └── 2024-01-15/
│           └── iPhone 12/
│               ├── IMG_001(1).jpg
│               └── IMG_002(1).jpg (low-res)
├── Videos/
│   ├── Original/
│   │   └── 2024-01-15/
│   │       └── mp4/
│   │           └── video.mp4
│   └── Duplicate/
├── EmptyFolder/
│   └── random_folder/
└── others/ (non-media files)
```

---

## ⚙️ Configuration

### Application Properties
**Location:** `src/main/resources/application.properties`

```properties
# Source folder
app.media-sorting.source-folder=${MEDIA_SOURCE_FOLDER:D:\\Images}

# Supported extensions
app.media-sorting.file-extensions.supported-image-extensions=\
  arw,jpg,jpeg,gif,bmp,ico,tif,tiff,raw,indd,ai,eps,pdf,heic,cr2,nrw,k25,png,webp

app.media-sorting.file-extensions.supported-video-extensions=\
  mp4,mkv,flv,avi,mov,wmv,rm,mpg,mpeg,3gp,vob,m4v,3g2,divx,xvid,webm

# Logging
logging.level.com.media.sort=INFO
app.media-sorting.root-logs-folder=logs
```

---

## 🔍 Recent Bug Fixes

### 1. Perceptual Duplicate Hash Map ✅
**Issue:** High-resolution files were placed in Duplicates  
**Fix:** Properly remove old hash from map when replacing with better quality  
**File:** `MediaFileWriter.java` lines 175-193

### 2. OS Duplicate Pattern Priority ✅
**Issue:** Clean filenames went to Duplicates while " - Copy" files stayed in Originals  
**Fix:** Use `isBetterQualityThan()` instead of `isAfter()` for all duplicates  
**File:** `MediaFileWriter.java` lines 98-147

### 3. Filename Cleaning ✅
**Issue:** Original files retained " - Copy" patterns  
**Fix:** Enhanced pattern removal + `cleanName=true` for all Original moves  
**File:** `MediaFileService.java` lines 214-260

### 4. Console Logging ✅
**Feature:** All console output saved to run-specific log directory  
**Files:** 
- `console.log` - Simple timestamped output
- `debug.log` - Detailed with thread and level info
**Location:** `logs/run_YYYY-MM-DD_HH-mm-ss/`

---

## 🧪 Testing Examples

### Example 1: Perceptual Duplicates
```
Input:
  ADLZ2152.JPG (1280×853, 317KB)
  ADLZ2152 - low.jpg (500×333, 507KB)

Output:
  ✅ Original/: ADLZ2152.JPG (higher resolution)
  ✅ Duplicate/: ADLZ2152 - low.jpg (lower resolution)
```

### Example 2: Exact Duplicates with Patterns
```
Input:
  AFBO7949.JPG
  AFBO7949 - Copy.JPG
  AFBO7949 - Copy (2).JPG

Output:
  ✅ Original/: AFBO7949.JPG (clean name, patterns removed)
  ✅ Duplicate/: AFBO7949(1).JPG (numbered)
  ✅ Duplicate/: AFBO7949(2).JPG (numbered)
```

---

## 🏗️ Architecture

### Key Components

**MediaFileReader** - Scans source folder, skips organized directories  
**MediaFileProcessor** - Computes SHA-256 + perceptual hash, extracts EXIF  
**MediaFileWriter** - Compares quality, moves files, manages hash map  
**PerceptualHashService** - DCT-based image hashing (threshold: 12 bits)  
**ExifData** - Quality comparison logic, OS pattern detection  
**MediaFileService** - File operations, filename cleaning  
**ProgressTrackerFactory** - Creates unique log directories per run

### Thread Safety
- `ConcurrentHashMap` for file tracking
- Synchronized unique filename generation
- Spring Batch chunk-based processing

---

## 📝 Logging

### Log Files (per run)
```
logs/run_YYYY-MM-DD_HH-mm-ss/
├── console.log              # Complete console output
├── debug.log                # Detailed debug info
├── po/
│   ├── file/compare.txt     # File comparison details
│   ├── image/error.txt      # Image processing errors
│   ├── video/error.txt      # Video processing errors
│   └── media/error.txt      # General media errors
└── cleanup/
    └── empty-folders.txt    # Empty folder cleanup log
```

### Quality Comparison Logs
```
[QUALITY] Comparing AFBO7949.JPG vs AFBO7949 - Copy (2).JPG
[QUALITY]   Pattern check: AFBO7949.JPG hasCopy=false, AFBO7949 - Copy (2).JPG hasCopy=true
[QUALITY]   Result: AFBO7949.JPG is BETTER (other has copy pattern)
```

---

## 🛠️ Development

### Build
```powershell
mvn clean package -DskipTests
```

### Run Tests
```powershell
mvn test
```

### Project Structure
```
src/main/java/com/media/sort/
├── batch/
│   ├── config/          # Spring Batch job configuration
│   ├── processor/       # File processing logic
│   ├── reader/          # File discovery
│   └── writer/          # File organization & duplicate handling
├── model/
│   └── ExifData.java    # Metadata model + quality comparison
├── service/
│   ├── PerceptualHashService.java
│   ├── MediaFileService.java
│   ├── ProgressTrackerFactory.java
│   └── ...
└── util/
    └── FileOperationUtils.java
```

---

## 📚 Technical Details

### Perceptual Hashing
- **Algorithm:** Discrete Cosine Transform (DCT)
- **Threshold:** Hamming distance ≤ 12 bits
- **Use Case:** Detect visually similar images with different resolutions
- **Limitation:** Images only (not videos)

### File Naming
- **Originals:** Remove `_1` to `_99` (preserves `_9515`)
- **Duplicates:** Add `(1)`, `(2)`, etc.
- **Conflict Resolution:** Compare dates, keep older as original

---

## 🚨 Known Limitations

1. **Video Perceptual Hashing:** Not implemented (exact duplicates only)
2. **Performance:** Large libraries (10K+ files) may be slow
3. **EXIF Date Threshold:** Files before 2006-01-01 are skipped

---

## 🎯 Future Enhancements

- [ ] Video perceptual hashing (frame-based)
- [ ] Configurable perceptual hash threshold
- [ ] Video quality comparison (resolution/bitrate)
- [ ] Resume capability for interrupted jobs
- [ ] Web UI for monitoring
- [ ] Batch size configuration

---

## 📄 License

This project is provided as-is for media organization purposes.

---

## 🙏 Credits

Built with:
- **Spring Boot 3.2.0** - Application framework
- **Spring Batch** - Batch processing
- **Apache Tika** - Metadata extraction
- **Commons Imaging** - Image processing
- **Metadata Extractor** - EXIF parsing

---

**For support or feature requests, please check the logs in `logs/run_*/console.log`**