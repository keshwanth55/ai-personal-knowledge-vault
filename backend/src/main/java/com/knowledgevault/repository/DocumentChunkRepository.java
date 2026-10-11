package com.knowledgevault.repository;

import com.knowledgevault.model.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {
    List<DocumentChunk> findAllByOwnerId(Long ownerId);
}
