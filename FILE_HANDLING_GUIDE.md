# File Handling Guide - Non-Media Files & Missing EXIF Data

## 📁 What Happens to Different File Types?

Your media sorting application handles **three categories** of files:

### 1. **Image Files** (Recognized Extensions)
- `.jpg`, `.jpeg`, `.png`, `.gif`, `.bmp`, `.heic`, `.tiff`, `.raw`, `.cr2`, `.nrw`, `.arw`, `.webp`
- **Action**: Moved to `Images/Original/` or `Images/Duplicate/` based on organization logic

### 2. **Video Files** (Recognized Extensions)
- `.mp4`, `.mov`, `.avi`, `.mkv`, `.wmv`, `.flv`, `.webm`, `.3gp`, `.m4v`, `.mpg`, `.mpeg`
- **Action**: Moved to `Videos/Original/` or `Videos/Duplicate/` based on organization logic

### 3. **Other Files** ❓
- **Everything else**: `.pdf`, `.txt`, `.doc`, `.zip`, `.exe`, etc.
- **Action**: Moved to `others/` directory

---

## 🗂️ Directory Structure Example

```
D:\YourPhotos\
├── Images\
│   ├── Original\
│   │   └── 2024\
│   │       └── 2024-12\
│   │           └── Canon-EOS-5D\
│   │               └── jpg\
│   │                   └── IMG_001.jpg
│   └── Duplicate\
│       └── ...
├── Videos\
│   ├── Original\
│   │   └── 2024\
│   │       └── mp4\
│   │           └── VID_001.mp4
│   └── Duplicate\
│       └── ...
└── others\              # ← Non-media files go here
    ├── document.pdf
    ├── readme.txt
    └── unknown.xyz
```

---

## 📅 What Happens When There's No EXIF Data?

The application uses a **date fallback waterfall** with **6 priority levels**:

### **Date Priority Waterfall:**

```
Priority 1: GPS Date (from satellite - most reliable, UTC)
    ↓
Priority 2: EXIF DateTimeOriginal (camera's capture time)
    ↓
Priority 3: EXIF DateTimeDigitized (when photo was digitized)
    ↓
Priority 4: QuickTime Creation Time (for HEIC files)
    ↓
Priority 5: XMP Metadata Date (for edited images, PNG, WebP)
    ↓
Priority 6: File System Dates (creation/modification times)
```

### **If ALL Date Sources Fail:**
- The file uses **file system dates** (creation or modification time)
- It's still organized, just without EXIF-based dating
- The file is tracked in the report as "no date" or "filesystem date"

---

## 🔍 Detailed Behavior by Scenario

### **Scenario 1: Image File with Full EXIF Data**
✅ **Best Case**
- **What Happens**: File is organized by EXIF date, device, and extension
- **Folder Path**: `Images/Original/2024/2024-12/Canon-EOS-5D/jpg/IMG_001.jpg`
- **Date Used**: EXIF DateTimeOriginal or GPS date

### **Scenario 2: Image File with Partial EXIF (No Date)**
⚠️ **Partial Data**
- **What Happens**: Uses filesystem dates as fallback
- **Folder Path**: `Images/Original/2024/2024-12/Unknown/jpg/IMG_002.jpg`
- **Date Used**: File creation or modification timestamp
- **Device**: Shows as "Unknown" if no camera info in EXIF

### **Scenario 3: Image File with NO EXIF at All**
⚠️ **No Metadata**
- **What Happens**: Organizes by file system dates
- **Folder Path**: `Images/Original/2024/2024-12/Unknown/jpg/screenshot.jpg`
- **Date Used**: File creation time from Windows/filesystem
- **Device**: "Unknown"
- **Note**: Still organized and processed normally!

### **Scenario 4: Video File**
✅ **Video Handling**
- **What Happens**: Extracts QuickTime metadata or uses filesystem dates
- **Folder Path**: `Videos/Original/2024/2024-12/mp4/VID_001.mp4`
- **Date Used**: QuickTime creation time → filesystem dates
- **Device**: Videos typically don't have device folders (configurable)

### **Scenario 5: Non-Media File (PDF, TXT, etc.)**
📄 **Other Files**
- **What Happens**: Moved to `others/` directory
- **Folder Path**: `others/document.pdf`
- **Organization**: **Not organized by date/device**
- **Processing**: Skipped for EXIF extraction (no error)

### **Scenario 6:** **Corrupted Image File**
❌ **Error Case**
- **What Happens**: EXIF extraction fails gracefully
- **Folder Path**: `Images/Original/2024/2024-12/Unknown/jpg/corrupted.jpg`
- **Fallback**: Uses filesystem dates
- **Logged**: Error logged in `logs/run_*/image-error.txt`

---

## 📊 What Gets Tracked in Reports?

The application tracks different date sources in the processing report:

```
DATE SOURCE BREAKDOWN:
  - GPS-dated: 150 files     (files with GPS timestamps)
  - EXIF-dated: 1200 files   (files with EXIF DateTimeOriginal)
  - Filesystem-dated: 50 files (files using file system dates)
  - No date: 5 files         (files with no usable date)

SPECIAL HANDLING:
  - Corrupt EXIF: 3 files    (files with corrupted EXIF data)
  - Unsupported format: 10 files (non-media files)
```

---

## ⚙️ Configuration

You can configure the "others" directory name in `application.properties`:

```properties
# Directory name for non-media files
app.media-sorting.directory-structure.others-directory-name=others
```

Change it to any name you want:
```properties
app.media-sorting.directory-structure.others-directory-name=Unsorted
# or
app.media-sorting.directory-structure.others-directory-name=Other_Files
```

---

## 🎯 Key Takeaways

### ✅ **Good News:**

1. **No file is left behind** - Everything gets organized somewhere
2. **Graceful fallbacks** - Missing EXIF? Uses filesystem dates
3. **No crashes** - Corrupted EXIF is handled gracefully
4. **Complete tracking** - All edge cases are logged and reported

### 📝 **What You'll See:**

- **Images without EXIF** → Organized by file dates in `Images/Original/.../Unknown/...`
- **Videos** → Usually organized by QuickTime metadata or file dates
- **Non-media files** → Moved to `others/` folder
- **Corrupted files** → Still organized, errors logged

### 🔧 **Error Logs:**

Errors and warnings are logged in:
- `logs/run_*/image-error.txt` - Image processing errors
- `logs/run_*/video-error.txt` - Video processing errors  
- `logs/application.log` - Complete application log

---

## 💡 **Examples**

### Example 1: Screenshot (No EXIF)
```
Input:  D:\Photos\screenshot.png (no EXIF data)
Output: D:\Photos\Images\Original\2024\2024-12-19\Unknown\png\screenshot.png
Date:   File creation time (2024-12-19)
Device: Unknown
```

### Example 2: Downloaded Image (No EXIF)
```
Input:  D:\Photos\wallpaper.jpg (no EXIF data)
Output: D:\Photos\Images\Original\2024\2024-12-19\Unknown\jpg\wallpaper.jpg
Date:   File modification time
Device: Unknown
```

### Example 3: PDF Document
```
Input:  D:\Photos\invoice.pdf
Output: D:\Photos\others\invoice.pdf
Date:   Not used for organization
Device: Not applicable
```

### Example 4: Camera Photo (Full EXIF)
```
Input:  D:\Photos\IMG_0001.jpg (with EXIF)
Output: D:\Photos\Images\Original\2019\2019-06-08\Canon-EOS-Rebel-T6\jpg\IMG_0001.jpg
Date:   EXIF DateTimeOriginal (2019-06-08)
Device: Canon EOS Rebel T6
```

---

## 🚀 **Summary**

Your application is **very robust** and handles edge cases gracefully:

- ✅ **Images/Videos**: Always organized (even without EXIF)
- ✅ **Non-media files**: Safely moved to `others/`
- ✅ **Missing dates**: Falls back to filesystem timestamps
- ✅ **Corrupted EXIF**: Logged but doesn't stop processing
- ✅ **Unknown devices**: Labeled as "Unknown"
- ✅ **Complete audit trail**: Everything is logged and tracked

**Bottom line**: Your application won't crash or skip files - it adapts!
