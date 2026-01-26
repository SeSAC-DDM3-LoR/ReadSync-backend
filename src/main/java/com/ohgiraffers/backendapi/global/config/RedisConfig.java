package com.ohgiraffers.backendapi.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
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

        // Key Serializer: "chatroom:1" 같은 키를 깔끔한 문자열로 저장
        template.setKeySerializer(new StringRedisSerializer());

        // Value Serializer: 객체(DTO)를 JSON 형태로 저장
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());

        // Hash Key/Value Serializer (Hash 사용할 경우 대비)
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());

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
}
