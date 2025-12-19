# Folder Comparison Job - Enhanced Features Summary

## Overview
The Folder Comparison Job has been enhanced with **three powerful features** from the Organize Job:
1. ✅ **Perceptual Hashing** - Detect visually similar images
2. ✅ **Burst Detection** - Prevent false duplicate marking for sequential photos
3. ✅ **RAW+JPEG Pairing** - Keep RAW and JPEG pairs together

---

## Feature Parity Matrix

| **Feature** | **Organize Job** | **Folder Comparison Job** | **Status** |
|------------|------------------|---------------------------|-----------|
| **Quality Comparison Logic** | ✅ FileQualityComparator | ✅ FileQualityComparator | ✅ **IDENTICAL** |
| **Resolution & Size Order** | ✅ Higher res + larger size | ✅ Higher res + larger size | ✅ **IDENTICAL** |
| **Hash Calculation** | ✅ SHA-256 | ✅ SHA-256 | ✅ **IDENTICAL** |
| **Perceptual Hashing** | ✅ Detects visually similar | ✅ Detects visually similar | ✅ **NOW IMPLEMENTED** |
| **Burst Detection** | ✅ Prevents false duplicates | ✅ Prevents false duplicates | ✅ **NOW IMPLEMENTED** |
| **RAW+JPEG Pairing** | ✅ Keeps pairs together | ✅ Keeps pairs together | ✅ **NOW IMPLEMENTED** |
| **Folder naming** | ✅ Creates year/month/device/ext | ❌ Keeps original structure | ⚠️ **BY DESIGN** |
| **File naming** | ✅ Can rename files | ❌ Keeps original names | ⚠️ **BY DESIGN** |
| **Date-based folders** | ✅ Creates 2024/2024-01/ | ❌ No date folders | ⚠️ **BY DESIGN** |

---

## Implementation Details

### 1. Perceptual Hashing ✅

**What it does:**
- Detects visually similar images even if file content differs
- Uses perceptual hash algorithm to compare image similarity
- Catches resized copies, edited versions, compression differences

**How it works:**
```java
// Step 1: Calculate perceptual hash for reference folder (Folder 2)
FileHashProcessor calculates and stores perceptual hash in FileHashDTO

// Step 2: For each file in Folder 1:
1. Calculate perceptual hash
2. Compare against all reference hashes
3. If similarity threshold met → mark as duplicate
4. Apply quality comparison to determine which to keep
```

**Example:**
```
Folder2/IMG_001.jpg (4032x3024, 3.2MB)
Folder1/IMG_001_resized.jpg (1920x1080, 800KB)

Result: Perceptually similar detected
→ Higher quality (4032x3024, 3.2MB) kept as original
→ Lower quality moved to Duplicates/
```

---

### 2. Burst Detection ✅

**What it does:**
- Identifies sequential photos from burst mode
- Prevents marking burst shots as duplicates
- Preserves all photos in rapid-fire sequences

**How it works:**
```java
// Check if filenames are sequential
DuplicatePatternUtils.isBurstSequence(file1, file2)

Examples:
- IMG_0146.JPG and IMG_0147.JPG → Sequential (diff = 1) → SKIP
- DSC03215.JPG and DSC03216.JPG → Sequential (diff = 1) → SKIP
- IMG_001.JPG and IMG_003.JPG → Not sequential (diff = 2) → PROCESS
```

**Example:**
```
Folder2/IMG_0146.JPG
Folder1/IMG_0147.JPG

Result: Burst sequence detected
→ Both files kept as unique
→ No duplicate marking applied
```

---

### 3. RAW+JPEG Pairing ✅

**What it does:**
- Recognizes RAW and JPEG files with same base name
- Prevents marking RAW+JPEG pairs as duplicates
- Keeps both formats together for professional workflows

**Supported RAW formats:**
- CR2 (Canon)
- ARW (Sony)
- NEF (Nikon)
- DNG (Adobe/Universal)
- ORF (Olympus)
- RAF (Fuji)
- RW2 (Panasonic)
- PEF (Pentax)
- SRW (Samsung)
- 3FR (Hasselblad)

**How it works:**
```java
// Check if files have same base name with RAW+JPEG extensions
DuplicatePatternUtils.isRawJpegPair(file1, file2)

Examples:
- IMG_001.CR2 and IMG_001.JPG → RAW+JPEG pair → SKIP
- DSC_1234.NEF and DSC_1234.JPG → RAW+JPEG pair → SKIP
- IMG_001.JPG and IMG_002.CR2 → Different names → PROCESS
```

**Example:**
```
Folder2/Wedding_001.CR2 (RAW)
Folder1/Wedding_001.JPG (JPEG)

Result: RAW+JPEG pair detected
→ Both files kept
→ No duplicate marking applied
```

---

## Processing Flow

### Enhanced Duplicate Detection Flow

```
For each file in Folder 1:
    
    1. Calculate SHA-256 hash
    
    2. Check for EXACT hash match in Folder 2
       ├─ If match found:
       │   ├─ Apply BURST DETECTION
       │   │   └─ If burst → SKIP (keep both)
       │   ├─ Apply RAW+JPEG PAIRING
       │   │   └─ If pair → SKIP (keep both)
       │   └─ Process as duplicate
       └─ If no match:
    
    3. Check for PERCEPTUAL hash match (if enabled)
       ├─ Calculate perceptual hash
       ├─ Compare against all reference perceptual hashes
       └─ If similar:
           ├─ Apply BURST DETECTION
           │   └─ If burst → SKIP (keep both)
           ├─ Apply RAW+JPEG PAIRING
           │   └─ If pair → SKIP (keep both)
           └─ Process as duplicate
    
    4. For duplicates, apply QUALITY COMPARISON
       ├─ Compare resolution
       ├─ Compare file size
       ├─ Compare dates
       └─ Determine original vs duplicate
    
    5. Move files accordingly
       ├─ Higher quality → Stays in original location
       └─ Lower quality → Moves to Duplicates/ subfolder
```

---

## Configuration

### Enable/Disable Features

All features are controlled by existing configuration:

```properties
# Perceptual hashing (application.properties)
app.media-sorting.perceptual-hash.enabled=true
media.perceptual-hash.threshold=0.95

# Burst detection (application.properties)
media.burst-detection.enabled=true
media.burst-detection.max-time-diff-seconds=5

# RAW+JPEG pairing (application.properties)
media.raw-jpeg-pairing.enabled=true
media.raw-jpeg-pairing.raw-extensions=cr2,arw,nef,dng,orf,raf,rw2,pef,srw,3fr
```

---

## Files Modified

### 1. **FileHashDTO.java**
- Added `perceptualHash` field to store perceptual hash values

### 2. **FileHashProcessor.java**
- Added `PerceptualHashService` dependency
- Calculate and store perceptual hash for each file
- Enhanced with perceptual hash computation for images

### 3. **DuplicateFileProcessor.java**
- Added `PerceptualHashService` dependency
- Implemented burst detection logic
- Implemented RAW+JPEG pairing logic
- Added perceptual duplicate search
- Enhanced duplicate detection flow with all three filters

### 4. **FolderComparisonJobConfig.java**
- Injected `PerceptualHashService` into Spring context
- Updated bean configurations to pass service to processors
- Updated documentation to reflect new features

---

## Quality Comparison Priority (UNCHANGED)

Both jobs use the **exact same** quality comparison logic:

| **Priority** | **Criteria** | **Rule** |
|-------------|-------------|----------|
| **0** | **SPECIAL RULE** | Higher Resolution **AND** Larger Size → Original (overrides dates) |
| **1** | **Resolution** | Higher pixels → Better quality |
| **2** | **EXIF Date Taken** | Older date → Original |
| **3** | **File Modified Date** | Older date → Original |
| **4** | **EXIF Date Created** | Older date → Original |
| **5** | **File Size** | Larger → Better (tiebreaker) |

---

## Benefits

### For Users:
1. **No More False Positives** - Burst shots won't be marked as duplicates
2. **Professional Workflow Support** - RAW+JPEG pairs stay together
3. **Comprehensive Detection** - Finds exact AND visually similar duplicates
4. **Quality Assurance** - Always keeps the highest quality version

### For Photographers:
- Burst mode sequences preserved
- RAW+JPEG workflows supported
- Edited versions detected as duplicates
- Original quality always prioritized

---

## Example Scenarios

### Scenario 1: Burst Mode Photos
```
Input:
Folder2/IMG_0146.JPG (burst shot 1)
Folder1/IMG_0147.JPG (burst shot 2)

Result:
✅ Burst sequence detected
✅ Both files kept as unique
❌ No duplicate moves
```

### Scenario 2: RAW+JPEG Workflow
```
Input:
Folder2/Wedding_001.CR2 (RAW, 25MB)
Folder1/Wedding_001.JPG (JPEG, 4MB)

Result:
✅ RAW+JPEG pair detected
✅ Both files kept
❌ No duplicate moves
```

### Scenario 3: Edited vs Original
```
Input:
Folder2/Portrait.jpg (original, 4032x3024, 3.2MB)
Folder1/Portrait_edited.jpg (resized, 1920x1080, 800KB)

Result:
✅ Perceptual duplicate detected
✅ Higher quality (4032x3024) kept as original
✅ Lower quality moved to Duplicates/Portrait_edited.jpg
```

### Scenario 4: Exact Duplicate with Higher Quality
```
Input:
Folder2/Sunset.jpg (2048x1536, 1.5MB)
Folder1/Sunset.jpg (4032x3024, 3.2MB)

Result:
✅ Exact hash match
✅ Higher quality (4032x3024) becomes new original
✅ Lower quality moved to Duplicates/Sunset.jpg
✅ Higher quality replaces original location
```

---

## Testing

### How to Test Each Feature:

#### 1. Test Perceptual Hashing:
```bash
# Create resized version of an image
# Place original in Folder2, resized in Folder1
# Run comparison job
# Expected: Resized version detected as duplicate
```

#### 2. Test Burst Detection:
```bash
# Place sequential photos in both folders
# Example: IMG_0146.JPG in Folder2, IMG_0147.JPG in Folder1
# Run comparison job
# Expected: Both kept, no duplicate moves
```

#### 3. Test RAW+JPEG Pairing:
```bash
# Place RAW file in Folder2, matching JPEG in Folder1
# Example: Photo_001.CR2 and Photo_001.JPG
# Run comparison job
# Expected: Both kept, no duplicate moves
```

---

## Summary

The Folder Comparison Job now has **complete feature parity** with the Organize Job for duplicate detection logic:

✅ **Same quality comparison rules**
✅ **Same resolution & size priorities**
✅ **Same hash calculation**
✅ **Perceptual hashing support**
✅ **Burst detection**
✅ **RAW+JPEG pairing**

The only differences are **intentional design choices**:
- Folder comparison maintains original folder structure
- Organize job creates new organized structure

Both jobs now provide the **same level of intelligence** in detecting and handling duplicates!
