package com.ohgiraffers.backendapi.domain.chapter.entity;

import com.ohgiraffers.backendapi.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

@Entity
@Table(name = "chapter_vectors_rag")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@SuperBuilder
public class ChapterVectorRag extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "rag_id")
    private Long ragId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chapter_id", nullable = false)
    private Chapter chapter;

    @JdbcTypeCode(SqlTypes.VECTOR)
    @Column(columnDefinition = "halfvec(1024)")
    private float[] vector;

    @Column(name = "content_chunk", columnDefinition = "TEXT", nullable = false)
    private String contentChunk;

    @Column(name = "chunk_index", nullable = false)
    private Integer chunkIndex;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "paragraph_ids", columnDefinition = "text[]")
    private List<String> paragraphIds;
}
