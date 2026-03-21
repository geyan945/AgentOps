package com.jobproj.agentops.integration;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class GeminiClient {

    @Value("${gemini.base-url}")
    private String baseUrl;

    @Value("${gemini.api-key:}")
    private String apiKey;

    @Value("${gemini.chat-model}")
    private String chatModel;

    public boolean isAvailable() {
        return StringUtils.hasText(apiKey);
    }

    public String currentModel() {
        return chatModel;
    }

    public String generateText(String prompt) {
        RestClient client = RestClient.builder().baseUrl(baseUrl).build();
        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt))))
        );
        JsonNode response = client.post()
                .uri(uriBuilder -> uriBuilder.path("/v1beta/" + chatModel + ":generateContent").queryParam("key", apiKey).build())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
        JsonNode textNode = response.path("candidates").path(0).path("content").path("parts").path(0).path("text");
        return textNode.isMissingNode() ? "" : textNode.asText("");
    }
}
