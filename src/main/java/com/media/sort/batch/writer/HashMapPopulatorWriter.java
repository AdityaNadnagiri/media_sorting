package com.media.sort.batch.writer;

import com.media.sort.batch.dto.FileHashDTO;
import com.media.sort.model.ExifData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ItemWriter that populates the shared hashMap with file hashes and metadata
 * from already-organized files.
 * 
 * This enables duplicate detection across multiple runs by building a reference
 * of all previously organized files before processing new files.
 */
@Slf4j
@RequiredArgsConstructor
public class HashMapPopulatorWriter implements ItemWriter<FileHashDTO> {

    private final Map<String, ExifData> fileHashMap;
    private final AtomicInteger populatedCount = new AtomicInteger(0);

    @Override
    public void write(Chunk<? extends FileHashDTO> chunk) throws Exception {
        for (FileHashDTO dto : chunk) {
            if (dto != null && dto.getHash() != null) {
                // Only add files with EXIF data (media files)
                // Skip files without EXIF (like text files, etc.)
                if (dto.getExifData() != null) {
                    fileHashMap.put(dto.getHash(), dto.getExifData());
                    populatedCount.incrementAndGet();

                    log.debug("Added to reference map: {} (hash: {})",
                            dto.getFilePath().getFileName(),
                            dto.getHash().substring(0, 8) + "...");
                }
            }
        }
    }

    /**
     * Get the total number of files added to the hash map
     */
    public int getPopulatedCount() {
        return populatedCount.get();
    }
}
