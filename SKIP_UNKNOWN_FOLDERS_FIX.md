# Unknown Folder Fix - Skip Instead of Create

## ✅ **What Was Changed**

Modified `FolderPatternResolver.java` to **skip folder levels** when information is unavailable instead of creating "Unknown" folders.

---

## 📋 **The Fix**

### **Before:**
```
Images/Original/2024/2024-12/Unknown/jpg/screenshot.jpg
                              ^^^^^^
                         "Unknown" folder created
```

### **After:**
```
Images/Original/2024/2024-12/jpg/screenshot.jpg
                              ↑
                    Device folder skipped entirely
```

---

## 🎯 **What Gets Skipped**

These folder tokens are now **skipped** when data is unavailable:

| Token | Skipped When | Example |
|-------|-------------|---------|
| `{device}` | No camera/device info | Screenshots, downloads |
| `{location}` | No GPS coordinates | Most photos |
| `{owner}` | No device mapping | When mapping disabled |

### **What's Never Skipped:**
- **Date folders** - Always created (uses filesystem dates as fallback)
- **Extension folder** - Always available from filename

---

## 📂 **Examples**

### Example 1: Camera Photo (Full Data)
```
Input:  Canon photo with EXIF
Output: Images/Original/2024/2024-12-19/Canon-EOS-5D/jpg/IMG_001.jpg
```
✅ All folders created

### Example 2: Screenshot (No Device)
```
Input:  Screenshot with no EXIF
Output: Images/Original/2024/2024-12-19/jpg/screenshot.jpg
                                         ↑
                              No "Unknown" folder!
```
✅ Device folder skipped

### Example 3: Downloaded Image
```  
Input:  Downloaded image (no device info)
Output: Images/Original/2024/2024-12-19/jpg/wallpaper.jpg
```
✅ Cleaner path structure

### Example 4: Video File
```
Input:  Video (typically no device info)
Output: Videos/Original/2024/2024-12-19/mp4/video.mp4
```
✅ No unnecessary folders

---

## 🔧 **How It Works**

The code now:
1. Returns **empty string** instead of "Unknown" for missing data
2. **Cleans up paths** by removing duplicate slashes: `2024//jpg` → `2024/jpg`
3. **Removes** leading/trailing slashes

```java
// OLD CODE:
return "Unknown";  // Creates Unknown folder

// NEW CODE:
return "";  // Skips this folder level
resolved = resolved.replaceAll("/+", "/");  // Clean up slashes
```

---

## 🚀 **To Use the Fix**

### **Rebuild:**
```bash
mvn clean package
```

### **Run:**
```bash
java -jar target\media-sorting-1.0.0-SNAPSHOT.jar --job=organize
```

### **Verify:**
Check your newly organized files - no more "Unknown" folders!

---

## ✨ **Benefits**

✅ **Cleaner structure** - No "Unknown" clutter  
✅ **Shorter paths** - Easier to navigate  
✅ **Consistent** - Matches expected behavior  
✅ **Flexible** - Works with any folder pattern

---

**Summary:** The app now creates folder levels **only when you have actual data for them**! 🎉
