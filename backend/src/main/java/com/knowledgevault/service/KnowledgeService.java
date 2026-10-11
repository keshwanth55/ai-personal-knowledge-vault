package com.knowledgevault.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgevault.model.Document;
import com.knowledgevault.model.DocumentChunk;
import com.knowledgevault.model.User;
import com.knowledgevault.repository.DocumentChunkRepository;
import com.knowledgevault.repository.DocumentRepository;
import com.knowledgevault.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class KnowledgeService {
    private static final Logger log = LoggerFactory.getLogger(KnowledgeService.class);
    private static final int CHUNK_SIZE = 900;
    private static final int CHUNK_OVERLAP = 120;
    private static final int MAX_RESULTS = 5;
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]{2,}");
    private final DocumentRepository documents;
    private final DocumentChunkRepository chunks;
    private final UserRepository users;
    private final DocumentTextExtractor extractor;
    private final EmbeddingService embeddings;
    private final RestClient ollama;
    private final ObjectMapper mapper;
    private final String chatModel;
    private final Path storageDirectory;

    public KnowledgeService(
            DocumentRepository documents,
            DocumentChunkRepository chunks,
            UserRepository users,
            DocumentTextExtractor extractor,
            EmbeddingService embeddings,
            ObjectMapper mapper,
            @Value("${vault.ollama.base-url}") String ollamaUrl,
            @Value("${vault.ollama.chat-model}") String chatModel,
            @Value("${vault.storage-directory}") String storageDirectory) {
        this.documents = documents;
        this.chunks = chunks;
        this.users = users;
        this.extractor = extractor;
        this.embeddings = embeddings;
        this.mapper = mapper;
        this.ollama = RestClient.builder().baseUrl(ollamaUrl).build();
        this.chatModel = chatModel;
        this.storageDirectory = Path.of(storageDirectory).toAbsolutePath().normalize();
    }

    @Transactional
    public Document upload(String email, MultipartFile file) {
        if (file.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The uploaded file is empty");
        String originalName = file.getOriginalFilename() == null ? "document" : Path.of(file.getOriginalFilename()).getFileName().toString();
        String lowerName = originalName.toLowerCase(Locale.ROOT);
        if (!(lowerName.endsWith(".pdf") || lowerName.endsWith(".txt") || lowerName.endsWith(".md")
                || lowerName.endsWith(".markdown") || lowerName.endsWith(".docx") || lowerName.endsWith(".pptx"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Supported files: PDF, TXT, Markdown, DOCX, PPTX");
        }

        User owner = user(email);
        String storedName = UUID.randomUUID().toString();
        Path storedFile = storageDirectory.resolve(storedName).normalize();
        try {
            String text = extractor.extract(file).replace("\u0000", "").trim();
            if (text.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No readable text was found in this file");
            }
            Files.createDirectories(storageDirectory);
            file.transferTo(storedFile);
            Document document = new Document(originalName, file.getContentType() == null ? "application/octet-stream" : file.getContentType(),
                    file.getSize(), storedName, owner);
            List<String> sections = split(text);
            for (int i = 0; i < sections.size(); i++) {
                List<Double> vector = embeddings.embed(sections.get(i));
                document.addChunk(new DocumentChunk(owner.getId(), i, sections.get(i), embeddings.serialize(vector)));
            }
            return documents.save(document);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read or store the uploaded document", exception);
        } catch (RuntimeException exception) {
            try { Files.deleteIfExists(storedFile); } catch (IOException cleanupFailure) {
                log.warn("Could not remove incomplete uploaded file {}", storedFile, cleanupFailure);
            }
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public List<Document> list(String email) {
        return documents.findAllByOwnerIdOrderByUploadedAtDesc(user(email).getId());
    }

    @Transactional
    public void delete(String email, Long documentId) {
        User owner = user(email);
        Document document = documents.findByIdAndOwnerId(documentId, owner.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
        Path storedFile = storageDirectory.resolve(document.getStoredPath()).normalize();
        try {
            if (!storedFile.startsWith(storageDirectory)) throw new IllegalStateException("Invalid stored document path");
            Files.deleteIfExists(storedFile);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not delete the stored document", exception);
        }
        documents.delete(document);
    }

    @Transactional(readOnly = true)
    public List<SearchHit> search(String email, String query) {
        if (query == null || query.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Search query is required");
        User owner = user(email);
        List<Double> queryVector = embeddings.embed(query);
        return retrieve(owner.getId(), query, queryVector);
    }

    @Transactional(readOnly = true)
    public ChatAnswer ask(String email, String question) {
        if (question == null || question.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Question is required");
        User owner = user(email);
        List<Double> queryVector = embeddings.embed(question);
        List<SearchHit> hits = retrieve(owner.getId(), question, queryVector);
        if (hits.isEmpty() || hits.get(0).score() <= 0) {
            return new ChatAnswer("I couldn't find relevant information in your uploaded documents. Try adding a related document or rephrasing your question.", List.of(), "retrieval_only");
        }

        String context = hits.stream()
                .map(hit -> "Source: " + hit.documentName() + "\n" + hit.excerpt())
                .reduce((first, second) -> first + "\n\n---\n\n" + second)
                .orElse("");
        try {
            var body = mapper.createObjectNode().put("model", chatModel).put("stream", false);
            var messages = body.putArray("messages");
            messages.addObject().put("role", "system").put("content",
                    "Answer the user only using the supplied excerpts from their private documents. " +
                    "If those excerpts do not contain the answer, say so. Do not invent facts. " +
                    "Be clear and concise, and cite source filenames in your answer.");
            messages.addObject().put("role", "user").put("content",
                    "Question: " + question + "\n\nDocument excerpts:\n" + context);
            JsonNode response = ollama.post().uri("/api/chat").body(body).retrieve().body(JsonNode.class);
            String answer = response == null ? "" : response.path("message").path("content").asText("");
            if (!answer.isBlank()) return new ChatAnswer(answer, hits, "ollama");
        } catch (RestClientException exception) {
            log.warn("Ollama chat unavailable; returning retrieved passages: {}", exception.getMessage());
        }
        String excerpts = hits.stream().limit(3)
                .map(hit -> "From " + hit.documentName() + ": “" + hit.excerpt() + "”")
                .reduce((first, second) -> first + "\n\n" + second).orElse("");
        return new ChatAnswer("I couldn't connect to the configured Ollama chat model, so this is a retrieval-only result:\n\n" + excerpts,
                hits, "retrieval_only");
    }

    private List<SearchHit> retrieve(Long ownerId, String query, List<Double> queryVector) {
        var tokens = new java.util.HashSet<String>();
        var matcher = WORD.matcher(query.toLowerCase(Locale.ROOT));
        while (matcher.find()) tokens.add(matcher.group());
        return chunks.findAllByOwnerId(ownerId).stream()
                .map(chunk -> {
                    List<Double> vector = embeddings.deserialize(chunk.getEmbedding());
                    double score = !queryVector.isEmpty() && queryVector.size() == vector.size()
                            ? cosine(queryVector, vector)
                            : keywordScore(chunk.getText(), tokens);
                    return new SearchHit(chunk.getId(), chunk.getDocument().getName(), excerpt(chunk.getText(), query),
                            score, chunk.getPosition());
                })
                .filter(hit -> hit.score() > 0)
                .sorted(Comparator.comparingDouble(SearchHit::score).reversed())
                .limit(MAX_RESULTS)
                .toList();
    }

    private double keywordScore(String text, java.util.Set<String> tokens) {
        if (tokens.isEmpty()) return 0;
        String lower = text.toLowerCase(Locale.ROOT);
        long matches = tokens.stream().filter(lower::contains).count();
        return (double) matches / tokens.size();
    }

    private double cosine(List<Double> left, List<Double> right) {
        double dot = 0, leftNorm = 0, rightNorm = 0;
        for (int i = 0; i < left.size(); i++) {
            dot += left.get(i) * right.get(i);
            leftNorm += left.get(i) * left.get(i);
            rightNorm += right.get(i) * right.get(i);
        }
        return leftNorm == 0 || rightNorm == 0 ? 0 : dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private String excerpt(String text, String query) {
        String normalized = text.replaceAll("\\s+", " ").trim();
        var matcher = WORD.matcher(query.toLowerCase(Locale.ROOT));
        int bestIndex = -1;
        while (matcher.find()) {
            int index = normalized.toLowerCase(Locale.ROOT).indexOf(matcher.group());
            if (index >= 0 && (bestIndex < 0 || index < bestIndex)) bestIndex = index;
        }
        int start = bestIndex < 0 ? 0 : Math.max(0, bestIndex - 100);
        int end = Math.min(normalized.length(), start + 360);
        return (start > 0 ? "…" : "") + normalized.substring(start, end) + (end < normalized.length() ? "…" : "");
    }

    private List<String> split(String text) {
        List<String> sections = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + CHUNK_SIZE, text.length());
            if (end < text.length()) {
                int boundary = Math.max(text.lastIndexOf(' ', end), text.lastIndexOf('\n', end));
                if (boundary > start + CHUNK_SIZE / 2) end = boundary;
            }
            String section = text.substring(start, end).trim();
            if (!section.isEmpty()) sections.add(section);
            if (end >= text.length()) break;
            start = Math.max(start + 1, end - CHUNK_OVERLAP);
        }
        return sections;
    }

    private User user(String email) {
        return users.findByEmail(email).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account not found"));
    }

    public record SearchHit(Long chunkId, String documentName, String excerpt, double score, int position) {}
    public record ChatAnswer(String content, List<SearchHit> sources, String mode) {}
}
