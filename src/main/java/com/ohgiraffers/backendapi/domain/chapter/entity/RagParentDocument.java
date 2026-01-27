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
@Table(name = "rag_parent_documents")
public class RagParentDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "parent_id")
    private Long id;

    @Column(name = "chapter_id", nullable = false)
    private Long chapterId;

    @Column(name = "content_text", columnDefinition = "TEXT", nullable = false)
    private String contentText;

    @Column(name = "speaker_list", columnDefinition = "text[]")
    @Type(ListArrayType.class)
    private List<String> speakerList;

    @Column(name = "paragraph_ids", columnDefinition = "text[]")
    @Type(ListArrayType.class)
    private List<String> paragraphIds;

    @Column(name = "start_paragraph_id", nullable = false, length = 50)
    private String startParagraphId;

    @Column(name = "end_paragraph_id", nullable = false, length = 50)
    private String endParagraphId;
}
