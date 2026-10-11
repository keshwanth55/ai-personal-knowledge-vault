package com.knowledgevault.web;

import com.knowledgevault.model.Document;
import com.knowledgevault.service.KnowledgeService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {
    private final KnowledgeService knowledge;

    public DocumentController(KnowledgeService knowledge) { this.knowledge = knowledge; }

    @GetMapping
    public List<DocumentResponse> list(Authentication authentication) {
        return knowledge.list(authentication.getName()).stream().map(DocumentResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentResponse upload(Authentication authentication, @RequestParam("file") MultipartFile file) {
        return DocumentResponse.from(knowledge.upload(authentication.getName(), file));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication authentication, @PathVariable Long id) {
        knowledge.delete(authentication.getName(), id);
    }

    public record DocumentResponse(Long id, String name, String contentType, long size, Instant uploadedAt, int chunkCount) {
        static DocumentResponse from(Document document) {
            return new DocumentResponse(document.getId(), document.getName(), document.getContentType(),
                    document.getSize(), document.getUploadedAt(), document.getChunks().size());
        }
    }
}
