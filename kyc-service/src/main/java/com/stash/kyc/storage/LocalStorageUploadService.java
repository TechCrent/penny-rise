package com.stash.kyc.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

@Service
public class LocalStorageUploadService {

    private final Path storageRoot;

    public LocalStorageUploadService(@Value("${stash.kyc.storage.local-root:./target/local-storage}") String storageRoot) {
        this.storageRoot = Paths.get(storageRoot).toAbsolutePath().normalize();
    }

    public void store(String relativePath, byte[] body) throws IOException {
        Path target = resolveWithinRoot(relativePath);
        Files.createDirectories(target.getParent());
        Files.write(target, body, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }

    /**
     * Reads a previously stored document. Used by the admin review view
     * endpoint to render document images.
     *
     * @throws java.nio.file.NoSuchFileException if the file does not exist
     */
    public byte[] read(String relativePath) throws IOException {
        Path target = resolveWithinRoot(relativePath);
        return Files.readAllBytes(target);
    }

    private Path resolveWithinRoot(String relativePath) {
        String normalizedPath = relativePath.replace("\\", "/").replaceFirst("^/", "");
        Path target = storageRoot.resolve(normalizedPath).normalize();
        if (!target.startsWith(storageRoot)) {
            throw new IllegalArgumentException("Invalid storage path: " + relativePath);
        }
        return target;
    }
}
