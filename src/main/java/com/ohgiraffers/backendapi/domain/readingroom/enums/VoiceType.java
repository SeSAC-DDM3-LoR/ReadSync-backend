package com.ohgiraffers.backendapi.domain.readingroom.enums;

/**
 * 독서룸에서 사용할 TTS 목소리 타입
 * Luxia API의 Voice ID와 매핑됨
 * 
 * Voice ID 목록:
 * - 76: Seonbi (차분한 남성 목소리)
 * - 2: Boram (따뜻한 여성 목소리)
 * - 5: Yuna (밝은 여성 목소리)
 * - 7: Kyeon (지적인 남성 목소리)
 * - 8: Bitna (청량한 여성 목소리)
 */
public enum VoiceType {
    SEONBI(76, "차분한 남성 목소리"),
    BORAM(2, "따뜻한 여성 목소리"),
    YUNA(5, "밝은 여성 목소리"),
    KYEON(7, "지적인 남성 목소리"),
    BITNA(8, "청량한 여성 목소리");

    private final int luxiaVoiceId;
    private final String description;

    VoiceType(int luxiaVoiceId, String description) {
        this.luxiaVoiceId = luxiaVoiceId;
        this.description = description;
    }

    public int getLuxiaVoiceId() {
        return luxiaVoiceId;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Luxia Voice ID로 VoiceType 찾기
     */
    public static VoiceType fromLuxiaVoiceId(int voiceId) {
        for (VoiceType type : values()) {
            if (type.luxiaVoiceId == voiceId) {
                return type;
            }
        }
        return SEONBI; // 기본값
    }
}
