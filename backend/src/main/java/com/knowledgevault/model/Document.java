package com.knowledgevault.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "documents")
public class Document {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String contentType;

    @Column(nullable = false)
    private long size;

    @Column(nullable = false)
    private String storedPath;

    @Column(nullable = false)
    private Instant uploadedAt = Instant.now();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User owner;

    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DocumentChunk> chunks = new ArrayList<>();

    protected Document() {}

    public Document(String name, String contentType, long size, String storedPath, User owner) {
        this.name = name;
        this.contentType = contentType;
        this.size = size;
        this.storedPath = storedPath;
        this.owner = owner;
    }

    public void addChunk(DocumentChunk chunk) {
        chunks.add(chunk);
        chunk.setDocument(this);
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getContentType() { return contentType; }
    public long getSize() { return size; }
    public String getStoredPath() { return storedPath; }
    public Instant getUploadedAt() { return uploadedAt; }
    public User getOwner() { return owner; }
    public List<DocumentChunk> getChunks() { return chunks; }
}
