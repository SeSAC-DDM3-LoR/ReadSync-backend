package com.ohgiraffers.backendapi.global.config;

import com.ohgiraffers.backendapi.domain.chat.listener.RedisMessageListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    /**
     * 객체(DTO)를 JSON으로 저장하는 범용 RedisTemplate
     * 채팅 메시지 등 복잡한 객체 저장에 사용
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // ObjectMapper 설정 (LocalDateTime 등 Java 8 날짜/시간 타입 지원)
        com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        objectMapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // GenericJackson2JsonRedisSerializer에 커스텀 ObjectMapper 적용
        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(objectMapper);

        // Key Serializer: "chatroom:1" 같은 키를 깔끔한 문자열로 저장
        template.setKeySerializer(new StringRedisSerializer());

        // Value Serializer: 객체(DTO)를 JSON 형태로 저장
        template.setValueSerializer(serializer);

        // Hash Key/Value Serializer (Hash 사용할 경우 대비)
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(serializer);

        return template;
    }

    /**
     * 문자열만 저장하는 StringRedisTemplate
     * UserStatusService 등 단순 문자열 저장에 사용
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    /**
     * Redis Pub/Sub 리스너 컨테이너
     * chatRoom:* 패턴의 모든 채널을 구독하여 메시지를 수신
     * user-kick 채널을 구독하여 강제 로그아웃 처리
     */
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            RedisMessageListener redisMessageListener,
            com.ohgiraffers.backendapi.domain.user.listener.UserKickListener userKickListener) {

        System.out.println("RedisMessageListenerContainer bean created");
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);

        // 1. chatRoom:* 패턴의 모든 채널 구독 (채팅 메시지)
        container.addMessageListener(redisMessageListener, new PatternTopic("chatRoom:*"));

        // 2. user-kick 채널 구독 (강제 로그아웃)
        container.addMessageListener(userKickListener, new PatternTopic("user-kick"));

        return container;
    }
}
