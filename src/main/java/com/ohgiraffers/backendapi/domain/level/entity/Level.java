package com.ohgiraffers.backendapi.domain.level.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "levels")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Level {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "level_id")
    private Long id;

    @Column(name = "required_exp", nullable = false)
    private Integer requiredExp;

    @Column(name = "max_comment_limit", nullable = false)
    private Integer maxCommentLimit;

    @Column(name = "can_upload_image", nullable = false)
    @Builder.Default
    private Boolean canUploadImage = false;
}
