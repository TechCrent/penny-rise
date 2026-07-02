package com.stash.platform.notification.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ExpoPushClient {

    private static final String EXPO_PUSH_URL = "https://exp.host/--/api/v2/push/send";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public ExpoPushClient(RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
        this.restClient   = restClientBuilder.baseUrl(EXPO_PUSH_URL).build();
        this.objectMapper = objectMapper;
    }

    public ExpoPushResult send(String expoPushToken, String title, String body, JsonNode data) {
        var request = objectMapper.createObjectNode()
                .put("to", expoPushToken)
                .put("title", title)
                .put("body", body);
        request.set("data", data);

        JsonNode response = restClient.post()
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(JsonNode.class);

        // Expo response: { "data": { "status": "ok"|"error", "details": { "error": "..." } } }
        JsonNode data0 = response.path("data");
        if ("ok".equals(data0.path("status").asText())) {
            return new ExpoPushResult(true, null);
        }
        String errorCode = data0.path("details").path("error").asText(null);
        return new ExpoPushResult(false, errorCode);
    }

    public record ExpoPushResult(boolean success, String errorCode) {}
}
