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
        String normalizedPath = relativePath.replace("\\", "/").replaceFirst("^/", "");
        Path target = storageRoot.resolve(normalizedPath).normalize();
        if (!target.startsWith(storageRoot)) {
            throw new IllegalArgumentException("Invalid storage path: " + relativePath);
        }
        Files.createDirectories(target.getParent());
        Files.write(target, body, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }
}
