package com.stash.kyc.storage;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class LocalFilesystemObjectStorageTest {

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void generateSignedUploadUrlUsesForwardedPublicOrigin() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-Proto", "http");
        request.addHeader("X-Forwarded-Host", "10.0.2.2:8080");
        RequestContextHolder.setRequestAttributes(
            new ServletRequestAttributes(request)
        );

        LocalFilesystemObjectStorage storage = new LocalFilesystemObjectStorage(
            "http://localhost:8082",
            "kyc-documents"
        );

        String url = storage.generateSignedUploadUrl(
            "submissions/123/front.jpg",
            "image/jpeg",
            Duration.ofMinutes(15)
        );

        assertThat(url).startsWith(
            "http://10.0.2.2:8080/internal/local-storage/upload/"
        );
        assertThat(url).contains("submissions/123/front.jpg");
    }
}
