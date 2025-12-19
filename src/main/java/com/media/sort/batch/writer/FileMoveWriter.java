package com.media.sort.batch.writer;

import com.media.sort.batch.dto.FileMoveDTO;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class FileMoveWriter implements ItemWriter<FileMoveDTO> {

    @Getter
    private final AtomicInteger movedCount = new AtomicInteger(0);

    @Getter
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
                    duplicatePath = duplicatesFolder.resolve("%s_%d%s".formatted(baseName, counter, extension));
                    counter++;
                }

                // Move reference to duplicates
                Files.move(referencePath, duplicatePath, StandardCopyOption.REPLACE_EXISTING);
                log.info("Moved lower quality file to duplicates: {} -> {}",
                        referencePath, duplicatePath);

                // Move source to replace reference location
                Files.move(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                log.info("Moved higher quality file to main folder: {} -> {}",
                        sourcePath, targetPath);

                replacedCount.incrementAndGet();
                movedCount.incrementAndGet();

            } else {
                // Reference is higher quality - move source to duplicates
                Files.move(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                log.info("Moved duplicate to folder: {} -> {}", sourcePath, targetPath);
                movedCount.incrementAndGet();
            }

        } catch (Exception e) {
            log.error("Error moving file: {} to {}", dto.getSourcePath(), dto.getTargetPath(), e);
        }
    }
}
