package com.knowledgevault.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import java.util.List;

@Service
public class EmbeddingService {
    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);
    private final RestClient client;
    private final String model;
    private final ObjectMapper mapper;

    public EmbeddingService(
            @Value("${vault.ollama.base-url}") String baseUrl,
            @Value("${vault.ollama.embedding-model}") String model,
            ObjectMapper mapper) {
        this.client = RestClient.builder().baseUrl(baseUrl).build();
        this.model = model;
        this.mapper = mapper;
    }

    public List<Double> embed(String text) {
        try {
            JsonNode response = client.post().uri("/api/embed")
                    .body(mapper.createObjectNode().put("model", model).put("input", text))
                    .retrieve().body(JsonNode.class);
            JsonNode vector = response == null ? null : response.path("embeddings").path(0);
            if (vector == null || !vector.isArray() || vector.isEmpty()) {
                throw new IllegalStateException("Ollama returned no embedding vector");
            }
            var values = new java.util.ArrayList<Double>(vector.size());
            vector.forEach(value -> values.add(value.asDouble()));
            return values;
        } catch (RestClientException | IllegalStateException exception) {
            log.warn("Embedding unavailable; using keyword retrieval for this content: {}", exception.getMessage());
            return List.of();
        }
    }

    public String serialize(List<Double> vector) {
        if (vector.isEmpty()) return "";
        try {
            return mapper.writeValueAsString(vector);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not store the embedding vector", exception);
        }
    }

    public List<Double> deserialize(String value) {
        if (value == null || value.isBlank()) return List.of();
        try {
            return mapper.readValue(value, mapper.getTypeFactory().constructCollectionType(List.class, Double.class));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not read a stored embedding vector", exception);
        }
    }
}
