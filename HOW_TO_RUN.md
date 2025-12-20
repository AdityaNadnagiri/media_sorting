# How to Run the Media Sorting Application

## 📋 Quick Start Guide

### **Option 1: Using Maven (Recommended for Development)**

> ⚠️ **IMPORTANT for PowerShell Users:**  
> PowerShell requires quotes around the entire `-D` parameter. Use this syntax:
> ```powershell
> mvn spring-boot:run "-Dspring-boot.run.arguments=--job=organize"
> ```
> Notice the quotes surround `-Dspring-boot...` not just the value.

#### Basic Organize Job:

**PowerShell (Windows):**
```powershell
mvn spring-boot:run "-Dspring-boot.run.arguments=--job=organize"
```

**CMD or Bash:**
```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--job=organize"
```

#### With Custom Source Folder:

**PowerShell (Windows):**
```powershell
mvn spring-boot:run "-Dspring-boot.run.arguments=--job=organize --app.media-sorting.source-folder=D:\YourPhotos"
```

**CMD or Bash:**
```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--job=organize --app.media-sorting.source-folder=D:\YourPhotos"
```

#### With Multiple Parameters:

**PowerShell (Windows):**
```powershell
mvn spring-boot:run "-Dspring-boot.run.arguments=--job=organize --app.media-sorting.source-folder=D:\YourPhotos --app.media-sorting.enable-device-folder-creation=false"
```

**CMD or Bash:**
```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--job=organize --app.media-sorting.source-folder=D:\YourPhotos --app.media-sorting.enable-device-folder-creation=false"
```

---

### **Option 2: Using the JAR File (Production)**

First, build the JAR:
```bash
mvn clean package
```

Then run:

#### Basic Organize Job:
```bash
java -jar target\media-sorting-1.0.0-SNAPSHOT.jar --job=organize
```

#### With Custom Source Folder:
```bash
java -jar target\media-sorting-1.0.0-SNAPSHOT.jar --job=organize --app.media-sorting.source-folder="D:\YourPhotos"
```

#### With Environment Variable (Easiest):
```bash
set MEDIA_SOURCE_FOLDER=D:\YourPhotos
java -jar target\media-sorting-1.0.0-SNAPSHOT.jar --job=organize
```

---

## 🎯 **Available Command-Line Parameters**

### **Job Selection** (Required)
- `--job=organize` - Organize media files from source folder
- `--job=compare` - Compare two folders for duplicates
- `--job=cleanup` - Cleanup operations
- `--undo=<sessionId>` - Undo a specific session
- `--list-sessions` - List available undo sessions

### **Source Folder Configuration**
- `--app.media-sorting.source-folder=<path>` - Override the default source folder
- Or use environment variable: `set MEDIA_SOURCE_FOLDER=<path>`
- Default: Uses value from application.properties

### **Folder Comparison Parameters** (for `--job=compare`)
- `--folder1.path=<path>` - Primary folder to check for duplicates
- `--folder2.path=<path>` - Secondary/reference folder

### **Feature Flags**
- `--app.media-sorting.enable-device-folder-creation=<true|false>` - Enable/disable device-specific folders
- `--app.media-sorting.enable-duplicate-moving=<true|false>` - Enable/disable moving duplicates
- `--app.media-sorting.enable-cross-run-duplicate-detection=<true|false>` - Enable/disable cross-run duplicate detection

---

## 📝 **Complete Examples**

### Example 1: Organize Photos from Specific Folder

**PowerShell (Windows):**
```powershell
# Using Maven
mvn spring-boot:run "-Dspring-boot.run.arguments=--job=organize --app.media-sorting.source-folder=D:\NewPhotos"

# Using JAR
java -jar target\media-sorting-1.0.0-SNAPSHOT.jar --job=organize --app.media-sorting.source-folder="D:\NewPhotos"

# Using Environment Variable (Recommended)
$env:MEDIA_SOURCE_FOLDER="D:\NewPhotos"
java -jar target\media-sorting-1.0.0-SNAPSHOT.jar --job=organize
```

**CMD (Windows):**
```cmd
# Using Maven
mvn spring-boot:run -Dspring-boot.run.arguments="--job=organize --app.media-sorting.source-folder=D:\NewPhotos"

# Using JAR
java -jar target\media-sorting-1.0.0-SNAPSHOT.jar --job=organize --app.media-sorting.source-folder="D:\NewPhotos"

# Using Environment Variable (Recommended)
set MEDIA_SOURCE_FOLDER=D:\NewPhotos
java -jar target\media-sorting-1.0.0-SNAPSHOT.jar --job=organize
```

### Example 2: Compare Two Folders

**PowerShell (Windows):**
```powershell
# Using Maven
mvn spring-boot:run "-Dspring-boot.run.arguments=--job=compare --folder1.path=D:\Folder1 --folder2.path=D:\Folder2"

# Using JAR
java -jar target\media-sorting-1.0.0-SNAPSHOT.jar --job=compare --folder1.path="D:\Folder1" --folder2.path="D:\Folder2"
```

**CMD (Windows):**
```cmd
# Using Maven
mvn spring-boot:run -Dspring-boot.run.arguments="--job=compare --folder1.path=D:\Folder1 --folder2.path=D:\Folder2"

# Using JAR
java -jar target\media-sorting-1.0.0-SNAPSHOT.jar --job=compare --folder1.path="D:\Folder1" --folder2.path="D:\Folder2"
```

### Example 3: Organize Without Device Folders
```bash
java -jar target\media-sorting-1.0.0-SNAPSHOT.jar --job=organize --app.media-sorting.source-folder="D:\Photos" --app.media-sorting.enable-device-folder-creation=false
```

### Example 4: List Undo Sessions
```bash
java -jar target\media-sorting-1.0.0-SNAPSHOT.jar --list-sessions
```

### Example 5: Undo a Session
```bash
java -jar target\media-sorting-1.0.0-SNAPSHOT.jar --undo=2025-12-19_123456
```

---

## 🔧 **Using Environment Variables**

You can also set environment variables instead of command-line args:

### Windows (PowerShell):
```powershell
$env:MEDIA_SOURCE_FOLDER="D:\YourPhotos"
java -jar target\media-sorting-1.0.0-SNAPSHOT.jar --job=organize
```

### Windows (CMD):
```cmd
set MEDIA_SOURCE_FOLDER=D:\YourPhotos
java -jar target\media-sorting-1.0.0-SNAPSHOT.jar --job=organize
```

### Linux/Mac:
```bash
export MEDIA_SOURCE_FOLDER="/path/to/photos"
java -jar target/media-sorting-1.0.0-SNAPSHOT.jar --job=organize
```

---

## 📂 **Directory Structure After Organization**

Your media will be organized like this:

```
D:\YourPhotos\
├── Images\
│   └── Original\
│       └── 2024\
│           └── 2024-12\
│               ├── Canon-EOS-5D\
│               │   └── jpg\
│               │       ├── IMG_001.jpg
│               │       └── IMG_002.jpg
│               └── iPhone-13\
│                   └── heic\
│                       └── IMG_100.heic
├── Videos\
│   └── Original\
│       └── 2024\
│           └── 2024-12\
│               └── mp4\
│                   └── VID_001.mp4
└── others\
    └── document.pdf
```

---

## ⚙️ **Configuration Priority**

The application uses this priority order for configuration:

1. **Command-line arguments** (highest priority)
2. **Environment variables**
3. **application.properties** (lowest priority)

Example:
```bash
# This overrides application.properties
java -jar target\media-sorting-1.0.0-SNAPSHOT.jar --job=organize --source.folder="D:\NewPhotos"
```

---

## 🚀 **Quick Reference**

### Most Common Use Cases:

#### 1. **Organize new photos from a folder:**
```bash
# Method 1: Using command-line parameter
java -jar target\media-sorting-1.0.0-SNAPSHOT.jar --job=organize --app.media-sorting.source-folder="D:\NewPhotos"

# Method 2: Using environment variable (Recommended)
set MEDIA_SOURCE_FOLDER=D:\NewPhotos
java -jar target\media-sorting-1.0.0-SNAPSHOT.jar --job=organize
```

#### 2. **Find duplicates between two folders:**
```bash
java -jar target\media-sorting-1.0.0-SNAPSHOT.jar --job=compare --folder1.path="D:\Folder1" --folder2.path="D:\Folder2"
```

#### 3. **Undo last organization:**
```bash
# First, list sessions
java -jar target\media-sorting-1.0.0-SNAPSHOT.jar --list-sessions

# Then undo
java -jar target\media-sorting-1.0.0-SNAPSHOT.jar --undo=2025-12-19_123456
```

---

## 💡 **Tips**

1. **Use Quotes** for paths with spaces:
   ```bash
   --source.folder="D:\My Photos\New Folder"
   ```

2. **Windows Paths** - Use double backslashes or forward slashes:
   ```bash
   --source.folder=D:\\Photos  # Double backslash
   --source.folder=D:/Photos   # Forward slash (also works on Windows)
   ```

3. **Check Logs** in the `logs/` directory for detailed information

4. **Transaction Logs** are saved in `transactions/` for undo capability

5. **Reports** are generated in `reports/` directory

---

## 📊 **What Happens During Execution**

1. **App starts** and reads configuration
2. **Job begins** based on `--job` parameter
3. **Files are scanned** from source folder
4. **EXIF data extracted** (dates, device info, GPS)
5. **Duplicates detected** (exact hash + perceptual + burst + RAW+JPEG)
6. **Quality comparison** performed
7. **Files organized** into Images/Videos folders
8. **Transaction log created** for undo capability
9. **Report generated** with statistics
10. **Summary displayed** in console

---

## 🎯 **Need Help?**

Run without parameters to see usage:
```bash
java -jar target\media-sorting-1.0.0-SNAPSHOT.jar
```

Output:
```
No job specified. Use --job=organize, --job=cleanup, or --job=compare
Or use: --undo=sessionId, --list-sessions
Application will exit.
```

---

## ✅ **Example Workflow**

```bash
# Step 1: Build the application
mvn clean package

# Step 2: Set your source folder
set MEDIA_SOURCE_FOLDER=D:\NewPhotos

# Step 3: Organize your photos
java -jar target\media-sorting-1.0.0-SNAPSHOT.jar --job=organize

# Step 4: Review the results in the logs
type logs\application.log

# Step 5: If needed, undo the operation
java -jar target\media-sorting-1.0.0-SNAPSHOT.jar --list-sessions
java -jar target\media-sorting-1.0.0-SNAPSHOT.jar --undo=2025-12-19_123456
```

---

**Your application is ready to use! Start organizing your media files now!** 🚀
