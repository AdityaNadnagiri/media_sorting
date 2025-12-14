package com.media.sort.batch.writer;

import com.media.sort.batch.dto.FileHashDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;

import java.util.concurrent.ConcurrentHashMap;

/**
 * ItemWriter that builds a hash map of file hashes to FileHashDTO objects.
 * Used in folder comparison to build reference map from folder2 with metadata.
 */
public class HashMapWriter implements ItemWriter<FileHashDTO> {

    private static final Logger logger = LoggerFactory.getLogger(HashMapWriter.class);

    private final ConcurrentHashMap<String, FileHashDTO> hashMap;

    public HashMapWriter(ConcurrentHashMap<String, FileHashDTO> hashMap) {
        this.hashMap = hashMap;
    }

    @Override
    public void write(Chunk<? extends FileHashDTO> chunk) throws Exception {
        for (FileHashDTO dto : chunk) {
            hashMap.put(dto.getHash(), dto);
            logger.debug("Added to hash map: {} -> {} (size: {} bytes)",
                    dto.getHash(), dto.getFilePath(), dto.getFileSize());
        }
    }
}
