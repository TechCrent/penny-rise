package com.stash.platform.kyc.api;

import com.stash.platform.kyc.client.KycServiceClient;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/internal/local-storage")
public class KycLocalStorageProxyController {

    private final KycServiceClient kycServiceClient;

    public KycLocalStorageProxyController(KycServiceClient kycServiceClient) {
        this.kycServiceClient = kycServiceClient;
    }

    @PutMapping(value = "/upload/{*path}", consumes = MediaType.ALL_VALUE)
    public ResponseEntity<byte[]> upload(@PathVariable("path") String path,
                                         HttpServletRequest request) throws IOException {
        String relativePath = path.startsWith("/") ? path.substring(1) : path;
        String proxyPath = "/internal/local-storage/upload/" + relativePath;
        String query = request.getQueryString();
        if (query != null && !query.isBlank()) {
            proxyPath += "?" + query;
        }

        MediaType contentType = null;
        if (request.getContentType() != null && !request.getContentType().isBlank()) {
            contentType = MediaType.parseMediaType(request.getContentType());
        }

        return kycServiceClient.forwardPublic(
                HttpMethod.PUT,
                proxyPath,
                request.getInputStream().readAllBytes(),
                contentType,
                null
        );
    }

    @GetMapping("/view/{*path}")
    public ResponseEntity<byte[]> view(@PathVariable("path") String path,
                                       HttpServletRequest request) {
        String relativePath = path.startsWith("/") ? path.substring(1) : path;
        String proxyPath = "/internal/local-storage/view/" + relativePath;
        String query = request.getQueryString();
        if (query != null && !query.isBlank()) {
            proxyPath += "?" + query;
        }

        return kycServiceClient.forwardPublic(
                HttpMethod.GET,
                proxyPath,
                null,
                null,
                null
        );
    }
}
