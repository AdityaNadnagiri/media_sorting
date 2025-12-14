# Media Sorting Application

A robust Spring Batch application for organizing media files (images and videos) with intelligent duplicate detection, smart renaming, and automatic empty folder cleanup.

---

## 🎯 Features

### **Core Functionality**
- ✅ **Automatic File Organization** - Organizes images and videos by date
- ✅ **Duplicate Detection** - Detects duplicates by file hash (SHA-256)
- ✅ **Smart Duplicate Handling** - Keeps OLDER files as originals, moves NEWER files to duplicates
- ✅ **Intelligent Renaming** - Originals get clean names, duplicates get numbered suffixes
- ✅ **Name Conflict Resolution** - Compares dates when same filename exists
- ✅ **Empty Folder Cleanup** - Automatically moves empty folders (never deletes)
- ✅ **Parallel Job Support** - Run multiple jobs simultaneously with unique log directories
- ✅ **Batch Mode** - Runs as a batch job and exits automatically

### **File Type Support**
- **Images**: JPG, JPEG, PNG, GIF, BMP, TIFF, CR2, NEF, ARW, DNG, RAF, ORF, RW2
- **Videos**: MP4, MOV, AVI, MKV, WMV, FLV, WEBM, M4V, 3GP, MPG, MPEG, QT, TGP
- **Others**: All other file types moved to `others/` folder

---

## 📁 Output Structure

```
SourceFolder/
├── Images/
│   ├── Original/
│   │   └── YYYY-MM-DD/
│   │       ├── DeviceModel/
│   │       │   ├── photo.jpg          ← Clean name (oldest version)
│   │       │   └── photo2.jpg
│   │       └── jpg/                   ← Files without device metadata
│   │           └── image.jpg
│   └── Duplicate/
│       └── YYYY-MM-DD/
│           └── DeviceModel/
│               ├── photo_1.jpg        ← Numbered suffix (newer duplicate)
│               └── photo_copy_1.jpg
├── Videos/
│   ├── Original/
│   │   └── YYYY-MM-DD/
│   │       └── video.mp4              ← Clean name (oldest version)
│   └── Duplicate/
│       └── YYYY-MM-DD/
│           └── video_1.mp4            ← Numbered suffix (newer duplicate)
├── EmptyFolder/
│   ├── OldFolder1/                    ← Empty folders moved here
│   └── OldFolder2/
├── others/
│   └── document.txt                   ← Non-media files
└── logs/
    ├── run_2025-12-14_15-40-49/       ← Unique logs per run
    └── run_2025-12-14_15-42-11/
```

---

## 🚀 Quick Start

### **Prerequisites**
- Java 17 or higher
- Maven 3.6+

### **Build**
```powershell
mvn clean install -DskipTests
```

### **Run**

**Organize Media Files:**
```powershell
java -jar target/media-sorting-1.0.0-SNAPSHOT.jar --job=organize --sourceFolder="D:\Images"
```

**Cleanup Empty Folders Only:**
```powershell
java -jar target/media-sorting-1.0.0-SNAPSHOT.jar --job=cleanup --targetFolder="D:\SomeFolder"
```

---

## 📋 How It Works

### **1. File Organization**
- Scans source folder recursively
- Extracts date from EXIF metadata (or uses file creation date as fallback)
- Organizes files into date-based folders
- Groups by device model (if available)

### **2. Duplicate Detection**
Files are considered duplicates if they have the **same SHA-256 hash** (identical content).

**Which file is kept as original?**
- The **OLDEST** file (by date taken/created/modified) is kept as original
- **NEWER** files are moved to Duplicate folder

**Example:**
```
File A: photo.jpg (taken 2023-01-01, hash: abc123) ← OLDER = ORIGINAL
File B: photo_copy.jpg (taken 2023-02-01, hash: abc123) ← NEWER = DUPLICATE

Result:
✅ Original/2023-01-01/photo.jpg       ← Oldest, clean name
✅ Duplicate/2023-02-01/photo_copy_1.jpg ← Newer, suffix added
```

### **3. Smart Renaming**

**Originals:**
- Suffixes removed (`_1`, `_2`, `_copy`, `_duplicate`, `(1)`, `(2)`)
- Clean filenames: `photo.jpg`, `video.mp4`

**Duplicates:**
- Numbered suffixes added: `photo_1.jpg`, `photo_2.jpg`
- Ensures no naming conflicts

### **4. Name Conflict Resolution**

When moving an original file, if another file with the same clean name already exists in Original folder:
1. Compare dates of both files
2. Keep **OLDER** file in Original (clean name)
3. Move **NEWER** file to Duplicate (with suffix)
4. Swap if needed

**Example:**
```
Current: Original/IMG_001.jpg (2023-02-01)
New: IMG_001_1.jpg (2023-01-01) ← Older!

Action:
1. Move existing to: Duplicate/IMG_001_1.jpg (2023-02-01)
2. Move new to: Original/IMG_001.jpg (2023-01-01)

Result: Oldest file has clean name in Original ✅
```

### **5. Empty Folder Cleanup**

After organizing files, the application automatically:
1. Scans for empty folders
2. Moves them to `EmptyFolder/` directory
3. Runs multiple passes until **no more empty folders** are found
4. Handles deeply nested empty folders

**Important:** Folders are **moved**, never deleted!

---

## 🔧 Configuration

### **Application Properties**

Edit `src/main/resources/application.properties`:

```properties
# Source folder (can be overridden by command line)
app.media-sorting.source-folder=D:/Images

# Log directory
app.media-sorting.root-logs-folder=logs

# Directory names
app.media-sorting.directory-structure.images-directory-name=Images
app.media-sorting.directory-structure.videos-directory-name=Videos
app.media-sorting.directory-structure.original-sub-directory-name=Original
app.media-sorting.directory-structure.duplicate-sub-directory-name=Duplicate
app.media-sorting.directory-structure.others-directory-name=others
app.media-sorting.directory-structure.empty-folder-directory-name=EmptyFolder

# Batch mode (exits after completion)
spring.main.web-application-type=none
spring.batch.job.enabled=true
```

---

## 📊 Parallel Job Execution

You can run multiple jobs simultaneously on different folders:

**Terminal 1:**
```powershell
java -jar target/media-sorting-1.0.0-SNAPSHOT.jar --job=organize --sourceFolder="D:\Images"
```

**Terminal 2:**
```powershell
java -jar target/media-sorting-1.0.0-SNAPSHOT.jar --job=organize --sourceFolder="D:\Photos"
```

**Terminal 3:**
```powershell
java -jar target/media-sorting-1.0.0-SNAPSHOT.jar --job=cleanup --targetFolder="D:\OldFiles"
```

Each job creates a unique log directory (`logs/run_YYYY-MM-DD_HH-mm-ss/`), preventing conflicts.

---

## 📝 Logs

### **Log Structure**
```
logs/
└── run_2025-12-14_15-40-49/    ← Unique per run
    ├── po/
    │   ├── file/
    │   │   └── compare.txt     ← File comparison logs
    │   └── video/
    │       ├── error.txt       ← Video processing errors
    │       ├── mp4Error.txt
    │       ├── qtError.txt
    │       └── otherError.txt
    ├── cleanup/
    │   └── empty-folders.txt   ← Empty folder cleanup logs
    └── progress.txt            ← General progress
```

### **Log Cleanup**
```powershell
# Keep only last 10 runs
Get-ChildItem logs -Directory | Sort-Object CreationTime -Descending | Select-Object -Skip 10 | Remove-Item -Recurse
```

---

## 🐛 Troubleshooting

### **Issue: Files not organized**
- Check that source folder exists and is accessible
- Verify file extensions are supported
- Check logs in `logs/run_*/` for errors

### **Issue: Duplicates not detected**
- Duplicates are detected by file **content** (hash), not name
- Files must have identical content to be considered duplicates
- Check `logs/run_*/po/file/compare.txt` for comparison details

### **Issue: Empty folders not moved**
- Folders must be completely empty (no files, no subdirectories)
- Check `logs/run_*/cleanup/empty-folders.txt` for details
- Cleanup runs multiple passes automatically

### **Issue: Parallel jobs conflict**
- Ensure you're using the latest version with unique log directories
- Each run should show: `Created unique log directory for this run: logs/run_...`

---

## 🎯 Examples

### **Example 1: Organize Wedding Photos**
```powershell
java -jar target/media-sorting-1.0.0-SNAPSHOT.jar --job=organize --sourceFolder="D:\Wedding"
```

**Result:**
```
D:\Wedding/
├── Images/
│   ├── Original/
│   │   ├── 2023-06-15/
│   │   │   ├── Canon EOS 5D/
│   │   │   │   ├── IMG_001.jpg
│   │   │   │   └── IMG_002.jpg
│   │   │   └── iPhone 12/
│   │   │       └── photo.jpg
│   │   └── 2023-06-16/
│   │       └── ...
│   └── Duplicate/
│       └── 2023-06-15/
│           └── IMG_001_1.jpg  ← Duplicate removed
└── EmptyFolder/
    └── OldBackup/             ← Empty folder moved
```

### **Example 2: Cleanup Old Archive**
```powershell
java -jar target/media-sorting-1.0.0-SNAPSHOT.jar --job=cleanup --targetFolder="D:\Archive"
```

**Result:**
- All empty folders moved to `D:\Archive\EmptyFolder\`
- Runs until no more empty folders found

---

## 📚 Technical Details

### **Technologies Used**
- **Spring Boot 3.2.0** - Application framework
- **Spring Batch** - Batch processing
- **Apache Commons Imaging** - EXIF metadata extraction
- **H2 Database** - In-memory database for batch job tracking

### **Architecture**
- **Reader**: Scans files recursively
- **Processor**: Extracts metadata, calculates hash
- **Writer**: Moves files to organized structure
- **Cleanup**: Runs after organization to move empty folders

### **Performance**
- Processes files in chunks (10 files per transaction)
- Parallel processing support
- Efficient hash calculation using SHA-256

---

## 🔒 Safety Features

1. **No Data Loss** - Files are moved, never deleted
2. **Empty Folders Preserved** - Moved to EmptyFolder directory
3. **Duplicate Safety** - Older files always kept as originals
4. **Conflict Resolution** - Smart date comparison prevents overwrites
5. **Transaction Support** - Batch processing with rollback on errors
6. **Unique Logs** - Each run isolated, no log conflicts

---

## 🎉 Success Criteria

After running the organize job, you should see:
- ✅ All media files organized by date
- ✅ Duplicates moved to Duplicate folders
- ✅ Originals have clean names
- ✅ Duplicates have numbered suffixes
- ✅ Empty folders moved to EmptyFolder directory
- ✅ Logs created in `logs/run_YYYY-MM-DD_HH-mm-ss/`
- ✅ Application exits automatically

---

## 📞 Support

For issues or questions:
1. Check the logs in `logs/run_*/`
2. Review this README
3. Check the troubleshooting section

---

## 📄 License

This project is for personal use.

---

## 🏆 Version History

### **v1.0.0** (Current)
- ✅ File organization by date
- ✅ Duplicate detection (older = original)
- ✅ Smart renaming (originals clean, duplicates numbered)
- ✅ Name conflict resolution
- ✅ Empty folder cleanup (continuous until all moved)
- ✅ Unique log directories per run
- ✅ Parallel job execution support
- ✅ Batch mode with auto-exit

---

**Ready to organize your media library!** 🚀