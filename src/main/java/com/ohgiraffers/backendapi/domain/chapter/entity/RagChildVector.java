package com.ohgiraffers.backendapi.domain.chapter.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;
import io.hypersistence.utils.hibernate.type.array.ListArrayType;

import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Table(name = "rag_child_vectors")
public class RagChildVector {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "child_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id", nullable = false)
    private RagParentDocument parent;

    @Column(columnDefinition = "vector(1024)")
    @org.hibernate.annotations.ColumnTransformer(write = "?::vector")
    @Convert(converter = com.ohgiraffers.backendapi.global.converter.VectorConverter.class)
    private List<Float> vector;

    @Column(name = "content_text", columnDefinition = "TEXT", nullable = false)
    private String contentText;

    @Column(name = "chunk_index", nullable = false)
    private Integer chunkIndex;

    @Column(name = "paragraph_ids", columnDefinition = "text[]")
    @Type(ListArrayType.class)
    private List<String> paragraphIds;

    @org.hibernate.annotations.CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private java.time.LocalDateTime createdAt;
}
