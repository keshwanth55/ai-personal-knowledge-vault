package com.knowledgevault.web;

import com.knowledgevault.service.KnowledgeService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class KnowledgeController {
    private final KnowledgeService knowledge;

    public KnowledgeController(KnowledgeService knowledge) { this.knowledge = knowledge; }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }

    @PostMapping("/chat")
    public KnowledgeService.ChatAnswer ask(Authentication authentication, @Valid @RequestBody Question request) {
        return knowledge.ask(authentication.getName(), request.question());
    }

    @GetMapping("/search")
    public List<KnowledgeService.SearchHit> search(Authentication authentication, @RequestParam("q") String query) {
        return knowledge.search(authentication.getName(), query);
    }

    public record Question(@NotBlank String question) {}
}
