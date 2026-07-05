package com.stash.kyc.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LocalStorageUploadControllerTest {

    @TempDir
    Path tempDir;

    @Test
    void putUploadPersistsBytesToDisk() throws Exception {
        var service = new LocalStorageUploadService(tempDir.toString());
        var controller = new LocalStorageUploadController(service);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        byte[] body = "hello-upload".getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(put("/internal/local-storage/upload/submissions/123/front.jpg")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(body))
                .andExpect(status().isCreated());

        Path storedFile = tempDir.resolve("submissions/123/front.jpg").toAbsolutePath().normalize();
        assertThat(Files.exists(storedFile)).isTrue();
        assertThat(Files.readAllBytes(storedFile)).isEqualTo(body);
    }

    @Test
    void getViewReturnsStoredBytesAsJpeg() throws Exception {
        var service = new LocalStorageUploadService(tempDir.toString());
        var controller = new LocalStorageUploadController(service);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        byte[] body = "fake-jpeg-bytes".getBytes(StandardCharsets.UTF_8);
        service.store("submissions/123/front.jpg", body);

        mockMvc.perform(get("/internal/local-storage/view/submissions/123/front.jpg"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(content().bytes(body));
    }

    @Test
    void getViewReturnsNotFoundForMissingFile() throws Exception {
        var service = new LocalStorageUploadService(tempDir.toString());
        var controller = new LocalStorageUploadController(service);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(get("/internal/local-storage/view/submissions/does/not-exist.jpg"))
                .andExpect(status().isNotFound());
    }
}
