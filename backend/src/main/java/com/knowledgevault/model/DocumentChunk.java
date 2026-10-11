package com.knowledgevault.model;

import jakarta.persistence.*;

@Entity
@Table(name = "document_chunks", indexes = {
        @Index(name = "idx_chunk_owner", columnList = "owner_id"),
        @Index(name = "idx_chunk_document", columnList = "document_id")
})
public class DocumentChunk {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Document document;

    @Column(nullable = false)
    private Long ownerId;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false, columnDefinition = "text")
    private String text;

    @Column(columnDefinition = "text")
    private String embedding;

    protected DocumentChunk() {}

    public DocumentChunk(Long ownerId, int position, String text, String embedding) {
        this.ownerId = ownerId;
        this.position = position;
        this.text = text;
        this.embedding = embedding;
    }

    void setDocument(Document document) { this.document = document; }
    public Long getId() { return id; }
    public Document getDocument() { return document; }
    public Long getOwnerId() { return ownerId; }
    public int getPosition() { return position; }
    public String getText() { return text; }
    public String getEmbedding() { return embedding; }
}
