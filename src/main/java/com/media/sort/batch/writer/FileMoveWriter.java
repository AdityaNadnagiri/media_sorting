package com.media.sort.batch.writer;

import com.media.sort.batch.dto.FileMoveDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Enhanced ItemWriter that moves files based on quality comparison.
 * Handles bidirectional moves: original to main folder, duplicate to Duplicates
 * subfolder.
 */
public class FileMoveWriter implements ItemWriter<FileMoveDTO> {

    private static final Logger logger = LoggerFactory.getLogger(FileMoveWriter.class);

    private final AtomicInteger movedCount = new AtomicInteger(0);
    private final AtomicInteger replacedCount = new AtomicInteger(0);

    @Override
    public void write(Chunk<? extends FileMoveDTO> chunk) throws Exception {
        for (FileMoveDTO dto : chunk) {
            moveFile(dto);
        }
    }

    private void moveFile(FileMoveDTO dto) {
        try {
            Path sourcePath = dto.getSourcePath();
            Path targetPath = dto.getTargetPath();
            Path referencePath = dto.getReferencePath();
            boolean sourceIsOriginal = dto.isSourceIsOriginal();

            // Create parent directories if they don't exist
            Files.createDirectories(targetPath.getParent());

            if (sourceIsOriginal) {
                // Source is higher quality - replace reference file
                // First, move reference to duplicates folder
                Path referenceParent = referencePath.getParent();
                Path duplicatesFolder = referenceParent.resolve("Duplicates");
                Files.createDirectories(duplicatesFolder);

                Path duplicatePath = duplicatesFolder.resolve(referencePath.getFileName());

                // Find unique name if needed
                int counter = 1;
                while (duplicatePath.toFile().exists()) {
                    String fileName = referencePath.getFileName().toString();
                    int dotIndex = fileName.lastIndexOf('.');
                    String baseName = dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
                    String extension = dotIndex > 0 ? fileName.substring(dotIndex) : "";
                    duplicatePath = duplicatesFolder.resolve(baseName + "_" + counter + extension);
                    counter++;
                }

                // Move reference to duplicates
                Files.move(referencePath, duplicatePath, StandardCopyOption.REPLACE_EXISTING);
                logger.info("Moved lower quality file to duplicates: {} -> {}",
                        referencePath.getFileName(), duplicatePath);

                // Move source to replace reference location
                Files.move(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                logger.info("Moved higher quality file to main folder: {} -> {}",
                        sourcePath.getFileName(), targetPath);

                replacedCount.incrementAndGet();
                movedCount.incrementAndGet();

            } else {
                // Reference is higher quality - move source to duplicates
                Files.move(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);

                movedCount.incrementAndGet();
            }

        } catch (Exception e) {
            logger.error("Error moving file: {} to {}",
                    dto.getSourcePath(), dto.getTargetPath(), e);
        }
    }

    public int getMovedCount() {
        return movedCount.get();
    }

    public int getReplacedCount() {
        return replacedCount.get();
    }
}
