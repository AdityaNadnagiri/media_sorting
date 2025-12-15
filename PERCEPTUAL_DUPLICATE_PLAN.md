# 🎨 Perceptual Duplicate Detection - Implementation Plan

## 🎯 Objective
Detect visually similar images and quality-duplicate videos using:
- **Images**: Perceptual hashing (pHash)
- **Videos**: Metadata comparison (resolution, bitrate, duration)

---

## ✅ Completed

1. ✅ Added JImageHash dependency to pom.xml
2. ✅ Created PerceptualHashService for image perceptual hashing

---

## 📋 Remaining Tasks

### Phase 1: Data Model Updates

**1. Update ExifData.java**
Add fields:
```java
private String perceptualHash;  // For images
private Integer imageWidth;     // For quality comparison
private Integer imageHeight;
private Long videoBitrate;      // For videos
private Integer videoWidth;
private Integer videoHeight;
private Double videoDuration;
```

**2. Add Quality Comparison Logic**
```java
public int getQualityScore() {
    if (isImage()) {
        return imageWidth * imageHeight; // Resolution
    } else {
        return (int) (videoBitrate / 1000); // Bitrate in kbps
    }
}

public boolean isBetterQualityThan(ExifData other) {
    return getQualityScore() > other.getQualityScore();
}
```

---

### Phase 2: Perceptual Hash Computation

**3. Update MediaFileProcessor.java**
```java
@Autowired
private PerceptualHashService perceptualHashService;

// In process() method:
if (isImage(file)) {
    String pHash = perceptualHashService.computeHash(file);
    exifData.setPerceptualHash(pHash);
    
    // Extract image dimensions
    BufferedImage img = ImageIO.read(file);
    exifData.setImageWidth(img.getWidth());
    exifData.setImageHeight(img.getHeight());
}
```

**4. Update VideoExifDataService.java**
```java
// Extract video metadata using Tika or FFmpeg
public void extractVideoMetadata(ExifData exifData) {
    // Get resolution
    exifData.setVideoWidth(metadata.get("width"));
    exifData.setVideoHeight(metadata.get("height"));
    
    // Get bitrate
    exifData.setVideoBitrate(metadata.get("bitrate"));
    
    // Get duration
    exifData.setVideoDuration(metadata.get("duration"));
}
```

---

### Phase 3: Duplicate Detection Logic

**5. Update MediaFileWriter.java**

Current logic uses SHA-256 hash:
```java
if (fileHashMap.containsKey(fileHash)) {
    // Found content duplicate
}
```

New logic adds perceptual checking:
```java
// Step 1: Check for exact content match (SHA-256)
if (fileHashMap.containsKey(fileHash)) {
    // Exact duplicate - existing logic
}

// Step 2: Check for perceptual duplicates (images only)
else if (isImage) {
    ExifData perceptualDuplicate = findPerceptualDuplicate(fileData, fileHashMap);
    if (perceptualDuplicate != null) {
        // Visually similar but different files
        // Compare quality scores
        if (fileData.isBetterQualityThan(perceptualDuplicate)) {
            // Current is better - swap
            moveToOriginal(fileData);
            moveToDuplicate(perceptualDuplicate);
        } else {
            // Existing is better - current to duplicate
            moveToDuplicate(fileData);
        }
    }
}

// Step 3: Check for video quality duplicates (similar name + duration)
else if (isVideo) {
    ExifData videoDuplicate = findVideoQualityDuplicate(fileData, fileHashMap);
    if (videoDuplicate != null) {
        // Similar video, compare quality
        if (fileData.isBetterQualityThan(videoDuplicate)) {
            moveToOriginal(fileData);
            moveToDuplicate(videoDuplicate);
        } else {
            moveToDuplicate(fileData);
        }
    }
}
```

**6. Helper Methods**
```java
private ExifData findPerceptualDuplicate(ExifData fileData, Map<String, ExifData> hashMap) {
    for (ExifData existing : hashMap.values()) {
        if (existing.getPerceptualHash() != null && 
            perceptualHashService.areSimilar(fileData.getPerceptualHash(), 
                                            existing.getPerceptualHash())) {
            return existing;
        }
    }
    return null;
}

private ExifData findVideoQualityDuplicate(ExifData fileData, Map<String, ExifData> hashMap) {
    for (ExifData existing : hashMap.values()) {
        // Check if similar name (base filename match)
        if (hasSimilarBaseName(fileData.getFile(), existing.getFile())) {
            // Check if similar duration (±2 seconds)
            if (Math.abs(fileData.getVideoDuration() - existing.getVideoDuration()) <= 2.0) {
                return existing;
            }
        }
    }
    return null;
}

private boolean hasSimilarBaseName(File file1, File file2) {
    String base1 = getBaseName(file1.getName()); // Remove extension + quality suffixes
    String base2 = getBaseName(file2.getName());
    return base1.equalsIgnoreCase(base2);
}
```

---

## 🔧 Configuration

**application.properties**
```properties
# Perceptual hash similarity threshold (0-32, lower = more strict)
app.media-sorting.perceptual-hash-threshold=10

# Enable/disable perceptual duplicate detection
app.media-sorting.enable-perceptual-detection=true

# Video duration tolerance for duplicates (seconds)
app.media-sorting.video-duration-tolerance=2.0
```

---

## ⚠️ Important Considerations

### Performance Impact
- **Images**: +50-100ms per image (perceptual hash computation)
- **Memory**: Larger hashMap (stores all processed files)
- **Video**: Minimal (metadata already extracted)

### Edge Cases to Handle
1. **Multiple similar images**: Keep highest quality
2. **Three-way duplicates**: A=B (perceptual), B=C (content), A≠C (content)
3. **False positives**: Very similar but distinct images
4. **Missing metadata**: Fallback to file size comparison

### Testing Strategy
1. Test with identical images at different resolutions
2. Test with cropped/edited versions
3. Test with videos transcoded to different qualities
4. Verify performance on large batches (1000+ files)

---

## 🚀 Deployment Steps

1. **Backup existing organized files**
2. **Update pom.xml** and rebuild: `mvn clean install`
3. **Run on test folder** first
4. **Verify results**:
   - Check Original/ has highest quality files
   - Check Duplicate/ has lower quality versions
5. **Review logs** for perceptual matches
6. **Adjust threshold** if needed (in properties)

---

## 📊 Expected Results

**Before (Content-based only):**
```
Original/
  ├── IMG_001.jpg (1920x1080, 2MB)
  ├── IMG_001_low.jpg (800x600, 500KB)  ← Both in Original!
```

**After (Perceptual + Quality):**
```
Original/
  ├── IMG_001.jpg (1920x1080, 2MB)  ← Highest quality

Duplicate/
  ├── IMG_001_low.jpg (800x600, 500KB)  ← Lower quality, perceptually similar
```

---

## 🎯 Next Steps

**This is a MAJOR feature.** Given the complexity, I recommend:

1. **Finish existing bugs first** (if any)
2. **Test current system** thoroughly
3. **Implement perceptual detection** in phases:
   - Phase 1: Data model (1-2 hours)
   - Phase 2: Hash computation (1-2 hours)
   - Phase 3: Duplicate logic (2-3 hours)
   - Phase 4: Testing + tuning (2-4 hours)

**Total estimated effort: 6-12 hours of development + testing**

Would you like me to:
- A) **Implement now** (continue tonight, will take several hours)
- B) **Create detailed code** for you to review/test tomorrow
- C) **Pause** and ensure current system is working perfectly first

Your choice! 🚀
