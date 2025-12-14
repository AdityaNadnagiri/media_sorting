# Quick Reference Guide

## 🚀 Common Commands

### **Organize Media Files**
```powershell
java -jar target/media-sorting-1.0.0-SNAPSHOT.jar --job=organize --sourceFolder="D:\Images"
```

### **Cleanup Empty Folders Only**
```powershell
java -jar target/media-sorting-1.0.0-SNAPSHOT.jar --job=cleanup --targetFolder="D:\Images"
```

### **Build Application**
```powershell
mvn clean install -DskipTests
```

---

## 📁 Quick Facts

| Feature | Behavior |
|---------|----------|
| **Duplicate Rule** | OLDER file = Original, NEWER file = Duplicate |
| **Naming** | Originals = clean names, Duplicates = numbered suffixes |
| **Empty Folders** | Moved to `EmptyFolder/` (never deleted) |
| **Logs** | Unique per run: `logs/run_YYYY-MM-DD_HH-mm-ss/` |
| **Parallel Jobs** | ✅ Supported (unique log directories) |
| **Exit Behavior** | Auto-exits after completion |

---

## 🎯 What Gets Organized

```
Images/Original/YYYY-MM-DD/DeviceModel/photo.jpg    ← Oldest version, clean name
Images/Duplicate/YYYY-MM-DD/DeviceModel/photo_1.jpg ← Newer version, suffix
Videos/Original/YYYY-MM-DD/video.mp4                ← Oldest version
Videos/Duplicate/YYYY-MM-DD/video_1.mp4             ← Newer version
EmptyFolder/OldFolder/                              ← Empty folders moved here
others/document.txt                                 ← Non-media files
```

---

## 🔍 Troubleshooting

| Issue | Solution |
|-------|----------|
| Files not organized | Check source folder path, verify file extensions |
| Duplicates not detected | Duplicates = same content (hash), not same name |
| Empty folders remain | Check logs, cleanup runs multiple passes automatically |
| Parallel job conflicts | Use latest version with unique log directories |

---

## 📊 Supported File Types

**Images:** JPG, JPEG, PNG, GIF, BMP, TIFF, CR2, NEF, ARW, DNG, RAF, ORF, RW2  
**Videos:** MP4, MOV, AVI, MKV, WMV, FLV, WEBM, M4V, 3GP, MPG, MPEG, QT, TGP

---

For full documentation, see [README.md](README.md)
