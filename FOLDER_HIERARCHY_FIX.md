# Folder Hierarchy Fix - Skip Unknown Folders

## ✅ **Change Summary**

### **What Changed:**
Modified `FolderPatternResolver.java` to **skip folder levels** when information is not available, instead of creating "Unknown" folders.

### **Before:**
```
Images/Original/2024/2024-12/Unknown/jpg/screenshot.jpg
                              ↑
                         Creates "Unknown" folder
```

### **After:**
```
Images/Original/2024/2024-12/jpg/screenshot.jpg
                              ↑
                         Skips device folder entirely
```

---

## 📋 **What Gets Skipped Now**

The following folder tokens are **skipped** (not created) when data is unavailable:

1. **`{device}`** - Skipped if no camera/device info
2. **`{location}`** - Skipped if no GPS coordinates
3. **`{owner}`** - Skipped if device owner mapping not available

### **Date Folders:**
- Date folders are **never skipped**
- If no EXIF date: uses filesystem dates
- **Date folders are always created** (using fallback dates if needed)

### **Extension Folder:**
- Extension is **always available** from filename
- **Extension folder is always created**

---

## 🗂️ **Examples After the Fix**

### Example 1: Photo with Full Data
```
Input: Camera photo with all EXIF data
Path:  Images/Original/2024/2024-12-19/Canon-EOS-5D/jpg/IMG_001.jpg
```
✅ **All folders created** - full information available

### Example 2: Screenshot (No Device Info)
```
Input: Screenshot with no EXIF
Path:  Images/Original/2024/2024-12-19/jpg/screenshot.jpg
                                         ↑
                              Device folder SKIPPED
```
✅ **Cleaner path** - no "Unknown" folder

### Example 3: Photo from Unknown Camera
```
Input: Photo with date but no device info
Path:  Images/Original/2019/2019-06-08/jpg/photo.jpg
                                        ↑
                             Device folder SKIPPED
```
✅ **Device folder skipped** - only date and extension

### Example 4: Video File
```
Input: Video file (videos don't typically have device info)
Path:  Videos/Original/2024/2024-12-19/mp4/video.mp4
                                         ↑
                              Device folder SKIPPED
```
✅ **Cleaner structure** for videos

---

## 🔧 **Technical Details**

### **Path Cleanup:**
The resolver now:
1. Replaces unknown tokens with **empty strings** (not "Unknown")
2. Removes **multiple consecutive slashes**: `//` → `/`
3. Removes **leading slashes**: `/2024/...` → `2024/...`
4. Removes **trailing slashes**: `.../jpg/` → `.../jpg`

### **Code Changes:**
```java
// BEFORE:
return "Unknown";  // Created "Unknown" folder

// AFTER:
return "";  // Skips this folder level
resolved = resolved.replaceAll("/+", "/");  // Clean up extra slashes
```

---

## 📊 **Folder Pattern Configuration**

Your default pattern is:
```
{year}/{year-month}/{device}/{extension}
```

### **Behavior with Missing Data:**

| Data Available | Result Path |
|----------------|-------------|
| All data | `2024/2024-12/Canon-EOS-5D/jpg` |
| No device | `2024/2024-12/jpg` ← **Device skipped** |
| No date (uses filesystem) | `2024/2024-12-19/Canon-EOS-5D/jpg` |
| No date, no device | `2024/2024-12-19/jpg` |

---

## 🎯 **Benefits**

### ✅ **Cleaner Directory Structure:**
- No more "Unknown" folders cluttering your organization
- Paths are shorter and more meaningful
- Easier to browse and find files

### ✅ **Consistent with Regular Flow:**
- Matches the behavior you expected
- Skips folders when data isn't available
- Only creates folders for which you have real data

### ✅ **Flexible:**
- Works with any folder pattern
- Handles optional tokens gracefully
- Maintains full path integrity

---

## 🚀 **How to Use**

### **Rebuild the Application:**
```bash
mvn clean package
```

### **Run with the Fix:**
```bash
java -jar target\media-sorting-1.0.0-SNAPSHOT.jar --job=organize --app.media-sorting.source-folder="D:\YourPhotos"
```

### **Verify:**
Check your organized files - you should see:
- ✅ No "Unknown" device folders
- ✅ Cleaner path structure
- ✅ Files still properly organized by date and extension

---

## 📝 **Notes**

1. **Existing Files**: Already-organized files with "Unknown" folders won't be moved automatically
2. **New Files**: All newly processed files will use the cleaner structure
3. **Customization**: You can change the folder pattern in configuration if needed
4. **Backwards Compatible**: This doesn't break anything - just creates cleaner paths

---

## ✨ **Summary**

**Before**: `Images/Original/2024/2024-12/Unknown/jpg/file.jpg`  
**After**: `Images/Original/2024/2024-12/jpg/file.jpg` ✅

The app now **skips folder levels** for unknown data instead of creating "Unknown" folders!
