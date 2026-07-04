package com.stash.kyc.storage;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/internal/local-storage")
public class LocalStorageUploadController {

    private final LocalStorageUploadService uploadService;

    public LocalStorageUploadController(LocalStorageUploadService uploadService) {
        this.uploadService = uploadService;
    }

    @PutMapping(value = "/upload/{*path}", consumes = MediaType.ALL_VALUE)
    public ResponseEntity<Void> upload(@PathVariable("path") String path,
                                       HttpServletRequest request) throws IOException {
        // {*path} captures everything after /upload/ including slashes; strip the leading slash.
        String relativePath = path.startsWith("/") ? path.substring(1) : path;
        byte[] body = request.getInputStream().readAllBytes();
        uploadService.store(relativePath, body);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
