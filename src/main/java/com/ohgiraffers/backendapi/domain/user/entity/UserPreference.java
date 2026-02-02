package com.ohgiraffers.backendapi.domain.user.entity;

import com.ohgiraffers.backendapi.domain.chapter.entity.Chapter;
import com.ohgiraffers.backendapi.global.common.BaseVectorEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_vectors")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserPreference extends BaseVectorEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId // User의 ID를 이 엔티티의 PK로 사용
    @JoinColumn(name = "user_id")
    private User user;

    // 1024차원 단기 취향 벡터 (DB의 real[] 또는 vector 타입에 매핑)
    @JdbcTypeCode(SqlTypes.VECTOR)
    @Column(name = "short_term_vector", columnDefinition = "halfvec(1024)")
    private float[] shortTermVector;

    /**
     * 유저 취향(장기/단기) 통합 업데이트 메서드
     * 
     * @param longTerm  새로운 장기 취향 벡터
     * @param shortTerm 새로운 단기 취향 벡터
     */
    public void updateTaste(float[] longTerm, float[] shortTerm) {
        // BaseVectorEntity의 vector 필드를 장기 취향으로 사용
        super.updateVector(longTerm);
        this.shortTermVector = shortTerm;
    }

    public UserPreference(User user) {
        this.user = user;
        super.updateVector(new float[1024]);
        this.shortTermVector = new float[1024];
    }

}