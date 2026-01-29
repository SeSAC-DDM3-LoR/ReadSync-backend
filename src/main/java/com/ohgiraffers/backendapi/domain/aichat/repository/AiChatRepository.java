package com.ohgiraffers.backendapi.domain.aichat.repository;

import com.ohgiraffers.backendapi.domain.aichat.entity.BookAiChat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AiChatRepository extends JpaRepository<BookAiChat, Long> {

    // 특정 채팅방의 메시지 목록 조회 (시간순)
    List<BookAiChat> findByChatRoom_AiRoomIdOrderByCreatedAtAsc(Long aiRoomId);
}
