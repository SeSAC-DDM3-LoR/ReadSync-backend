package com.ohgiraffers.backendapi.domain.aichat.service;

import com.ohgiraffers.backendapi.domain.aichat.dto.*;
import com.ohgiraffers.backendapi.domain.aichat.entity.BookAiChat;
import com.ohgiraffers.backendapi.domain.aichat.entity.BookAiChatRoom;
import com.ohgiraffers.backendapi.domain.aichat.enums.ChatType;
import com.ohgiraffers.backendapi.domain.aichat.repository.BookAiChatRepository;
import com.ohgiraffers.backendapi.domain.aichat.repository.BookAiChatRoomRepository;
import com.ohgiraffers.backendapi.domain.chapter.entity.Chapter;
import com.ohgiraffers.backendapi.domain.chapter.repository.ChapterRepository;
import com.ohgiraffers.backendapi.domain.chapter.service.ChapterVectorRagService;
import com.ohgiraffers.backendapi.domain.chapter.dto.rag.RagSearchResponseDTO;
import com.ohgiraffers.backendapi.domain.user.entity.User;
import com.ohgiraffers.backendapi.domain.user.repository.UserRepository;
import com.ohgiraffers.backendapi.global.error.CustomException;
import com.ohgiraffers.backendapi.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AI 채팅 서비스
 * 채팅방 관리 및 Python AI 서버와의 통신을 담당합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookAiChatService {

    private final BookAiChatRoomRepository chatRoomRepository;
    private final BookAiChatRepository chatRepository;
    private final UserRepository userRepository;
    private final ChapterRepository chapterRepository;
    private final ChapterVectorRagService ragService;
    private final WebClient aiWebClient;

    @Value("${ai.server.timeout:30}")
    private int aiServerTimeout;

    /* ========== 채팅방 관리 ========== */

    /**
     * [1] 채팅방 생성
     * 동일한 사용자+챕터 조합의 채팅방이 있으면 기존 채팅방 반환
     */
    @Transactional
    public BookAiChatRoomResponseDTO createChatRoom(Long userId, BookAiChatRoomRequestDTO request) {
        User user = findUserById(userId);
        Chapter chapter = findChapterById(request.getChapterId());

        // 기존 채팅방 확인
        return chatRoomRepository.findByUserAndChapter(user, chapter)
                .map(BookAiChatRoomResponseDTO::from)
                .orElseGet(() -> {
                    // 새 채팅방 생성
                    String title = (request.getTitle() != null && !request.getTitle().isEmpty())
                            ? request.getTitle()
                            : BookAiChatRoom.generateDefaultTitle(chapter);

                    BookAiChatRoom room = BookAiChatRoom.builder()
                            .user(user)
                            .chapter(chapter)
                            .title(title)
                            .build();

                    return BookAiChatRoomResponseDTO.from(chatRoomRepository.save(room));
                });
    }

    /**
     * [2] 채팅방 단건 조회
     */
    public BookAiChatRoomResponseDTO getChatRoom(Long userId, Long roomId) {
        BookAiChatRoom room = findChatRoomById(roomId);
        validateRoomOwner(room, userId);

        long messageCount = chatRepository.countByChatRoom(room);
        return BookAiChatRoomResponseDTO.from(room, messageCount);
    }

    /**
     * [3] 사용자의 채팅방 목록 조회
     */
    public Page<BookAiChatRoomResponseDTO> getChatRoomsByUser(Long userId, Pageable pageable) {
        return chatRoomRepository.findByUserId(userId, pageable)
                .map(BookAiChatRoomResponseDTO::from);
    }

    /**
     * [4] 채팅방 삭제 (Soft Delete)
     */
    @Transactional
    public void deleteChatRoom(Long userId, Long roomId) {
        BookAiChatRoom room = findChatRoomById(roomId);
        validateRoomOwner(room, userId);
        room.delete();
    }

    /**
     * [5] 채팅방 제목 수정
     */
    @Transactional
    public BookAiChatRoomResponseDTO updateChatRoomTitle(Long userId, Long roomId, String newTitle) {
        BookAiChatRoom room = findChatRoomById(roomId);
        validateRoomOwner(room, userId);
        room.updateTitle(newTitle);
        return BookAiChatRoomResponseDTO.from(room);
    }

    /* ========== 채팅 메시지 관리 ========== */

    /**
     * [6] 채팅 기록 조회
     */
    public List<BookAiChatResponseDTO> getChatHistory(Long userId, Long roomId) {
        BookAiChatRoom room = findChatRoomById(roomId);
        validateRoomOwner(room, userId);

        return chatRepository.findByChatRoomOrderByCreatedAtAsc(room).stream()
                .map(BookAiChatResponseDTO::from)
                .collect(Collectors.toList());
    }

    /**
     * [7] 채팅 기록 페이징 조회
     */
    public Page<BookAiChatResponseDTO> getChatHistoryPaged(Long userId, Long roomId, Pageable pageable) {
        BookAiChatRoom room = findChatRoomById(roomId);
        validateRoomOwner(room, userId);

        return chatRepository.findByChatRoom(room, pageable)
                .map(BookAiChatResponseDTO::from);
    }

    /**
     * [8] 메시지 전송 (일반 HTTP 요청)
     * Python AI 서버에 요청을 보내고 응답을 받아 DB에 저장합니다.
     */
    @Transactional
    public BookAiChatResponseDTO sendMessage(Long userId, Long roomId, BookAiChatRequestDTO request) {
        BookAiChatRoom room = findChatRoomById(roomId);
        validateRoomOwner(room, userId);
        User user = findUserById(userId);

        long startTime = System.currentTimeMillis();

        // 1. Chat Type 분류 (Python 서버 호출)
        ChatType chatType = request.getChatType();
        if (chatType == null) {
            chatType = classifyChatType(request.getUserMessage(), room.getChapter().getChapterId(),
                    request.getCurrentParagraphId());
        }

        // 2. RAG 검색 및 쿼리 재작성
        String ragContext = "";
        String relatedParagraphId = null;
        String searchQuery = request.getUserMessage();

        // 문맥이 필요한 질문이면 쿼리 재작성 시도
        if (ChatType.CONTENT_QA_CONTEXT.equals(chatType)) {
            searchQuery = rewriteQueryIfNeeded(room, request.getUserMessage());
            // 재작성 후에는 일반 QA로 취급하여 처리 (선택 사항)
        }

        if (ChatType.CONTENT_QA.equals(chatType) || ChatType.CONTENT_QA_CONTEXT.equals(chatType)
                || ChatType.SUMMARY.equals(chatType)) {
            List<RagSearchResponseDTO> searchResults = ragService.searchRag(room.getChapter().getChapterId(),
                    searchQuery);

            if (!searchResults.isEmpty()) {
                // 상위 3개 정도만 컨텍스트로 사용
                ragContext = searchResults.stream()
                        .limit(3)
                        .map(dto -> dto.getContentText())
                        .collect(Collectors.joining("\n---\n"));

                // 첫 번째 결과의 ID를 출처로 사용
                relatedParagraphId = searchResults.get(0).getStartParagraphId();
            }
        }

        try {
            // 이전 대화 내역 조회 및 변환 function call
            List<Map<String, String>> previousMessages = getPreviousMessages(room);

            // Python AI 서버 호출
            AiChatDTO.AiGenerateResponse aiResponse = callAiServerV2(
                    request.getUserMessage(),
                    chatType,
                    ragContext,
                    previousMessages);

            long responseTimeMs = System.currentTimeMillis() - startTime;

            // DB에 저장
            BookAiChat chat = BookAiChat.builder()
                    .chatRoom(room)
                    .user(user)
                    .chatType(chatType)
                    .userMessage(request.getUserMessage())
                    .aiMessage(aiResponse.getResponse())
                    .tokenCount(aiResponse.getToken_usage())
                    .responseTimeMs((int) responseTimeMs)
                    .build();

            BookAiChat savedChat = chatRepository.save(chat);

            return BookAiChatResponseDTO.from(savedChat)
                    .toBuilder()
                    .relatedParagraphId(relatedParagraphId)
                    .build();

        } catch (WebClientResponseException e) {
            log.error("AI 서버 호출 실패: Status {}, Body: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new CustomException(ErrorCode.AI_SERVER_ERROR);
        }
    }

    /**
     * [9] 메시지 전송 (SSE 스트리밍)
     * Python AI 서버에서 스트리밍 응답을 받아 클라이언트에 전달합니다.
     * 스트리밍 완료 후 DB에 저장합니다.
     */
    @Transactional
    public Flux<String> sendMessageStream(Long userId, Long roomId, BookAiChatRequestDTO request) {
        BookAiChatRoom room = findChatRoomById(roomId);
        validateRoomOwner(room, userId);
        User user = findUserById(userId);

        long startTime = System.currentTimeMillis();
        StringBuilder fullResponse = new StringBuilder();

        // 1. Chat Type 분류 (스트리밍에서도 분류 필요)
        ChatType detectedChatType = request.getChatType();
        if (detectedChatType == null) {
            detectedChatType = classifyChatType(request.getUserMessage(), room.getChapter().getChapterId(),
                    request.getCurrentParagraphId());
        }
        final ChatType chatType = detectedChatType; // for lambda

        // 2. RAG 검색 및 쿼리 재작성 (스트리밍 전 동기 처리)
        String ragContext = "";
        String searchQuery = request.getUserMessage();

        if (ChatType.CONTENT_QA_CONTEXT.equals(chatType)) {
            searchQuery = rewriteQueryIfNeeded(room, request.getUserMessage());
        }

        if (ChatType.CONTENT_QA.equals(chatType) || ChatType.CONTENT_QA_CONTEXT.equals(chatType)
                || ChatType.SUMMARY.equals(chatType)) {
            List<RagSearchResponseDTO> searchResults = ragService.searchRag(room.getChapter().getChapterId(),
                    searchQuery);
            if (!searchResults.isEmpty()) {
                ragContext = searchResults.stream()
                        .limit(3)
                        .map(dto -> dto.getContentText())
                        .collect(Collectors.joining("\n---\n"));
            }
        }
        final String finalRagContext = ragContext; // for lambda

        // 이전 대화 내역 조회
        List<Map<String, String>> previousMessages = getPreviousMessages(room);

        return callAiServerStream(
                room.getChapter().getChapterId(),
                request.getUserMessage(),
                chatType,
                previousMessages,
                finalRagContext) // ragContext 추가 전달
                .doOnNext(chunk -> fullResponse.append(chunk))
                .doOnComplete(() -> {
                    long responseTimeMs = System.currentTimeMillis() - startTime;

                    // 스트리밍 완료 후 DB에 저장
                    BookAiChat chat = BookAiChat.builder()
                            .chatRoom(room)
                            .user(user)
                            .chatType(chatType)
                            .userMessage(request.getUserMessage())
                            .aiMessage(fullResponse.toString())
                            .responseTimeMs((int) responseTimeMs)
                            .build();

                    chatRepository.save(chat);
                    log.info("AI 채팅 저장 완료 - roomId: {}, 응답시간: {}ms", roomId, responseTimeMs);
                })
                .doOnError(e -> {
                    log.error("AI 스트리밍 실패: {}", e.getMessage());
                });
    }

    /**
     * [10] AI 답변 평가
     */
    @Transactional
    public void rateAiResponse(Long userId, Long chatId, Integer rating) {
        // 평점 유효성 검증
        if (rating == null || rating < 1 || rating > 5) {
            throw new CustomException(ErrorCode.AI_RATING_INVALID);
        }

        BookAiChat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new CustomException(ErrorCode.AI_CHAT_NOT_FOUND));

        // 소유자 검증
        if (!chat.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.AI_CHAT_NOT_OWNER);
        }

        chat.updateRating(rating);
    }

    /* ========== Python AI 서버 통신 ========== */

    /**
     * Python AI 서버 호출 (일반 HTTP)
     */
    private Map<String, Object> callAiServer(Long chapterId, String userMessage, ChatType chatType) {
        return aiWebClient.post()
                .uri("/generate-answer")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "chapter_id", chapterId,
                        "user_message", userMessage,
                        "chat_type", chatType != null ? chatType.name() : ChatType.CONTENT_QA.name()))
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(aiServerTimeout))
                .block();
    }

    /**
     * Python AI 서버 호출 (SSE 스트리밍)
     */
    private Flux<String> callAiServerStream(Long chapterId, String userMessage, ChatType chatType,
            List<Map<String, String>> previousMessages, String ragContext) {
        return aiWebClient.post()
                .uri("/api/v1/chat/generate/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "user_msg", userMessage,
                        "chat_type", chatType != null ? chatType.name() : ChatType.CONTENT_QA.name(),
                        "previous_messages", previousMessages != null ? previousMessages : List.of(),
                        "rag_context", ragContext != null ? ragContext : ""))
                .accept(MediaType.TEXT_EVENT_STREAM)
                .retrieve()
                .bodyToFlux(String.class)
                .timeout(Duration.ofSeconds(aiServerTimeout));
    }

    /* ========== Python AI 서버 통신 (New API) ========== */

    private ChatType classifyChatType(String userMsg, Long chapterId, String paragraphContent) {
        try {
            AiChatDTO.AiClassifyRequest req = new AiChatDTO.AiClassifyRequest(userMsg, chapterId, paragraphContent);

            AiChatDTO.AiClassifyResponse res = aiWebClient.post()
                    .uri("/api/v1/chat/classify")
                    .bodyValue(req)
                    .retrieve()
                    .bodyToMono(AiChatDTO.AiClassifyResponse.class)
                    .block();

            if (res != null && res.getChat_type() != null) {
                return ChatType.valueOf(res.getChat_type());
            }
        } catch (Exception e) {
            log.error("AI classification failed", e);
        }
        return ChatType.CHIT_CHAT;
    }

    private AiChatDTO.AiGenerateResponse callAiServerV2(String userMsg, ChatType chatType, String ragContext,
            List<Map<String, String>> previousMessages) {
        AiChatDTO.AiGenerateRequest req = AiChatDTO.AiGenerateRequest.builder()
                .user_msg(userMsg)
                .chat_type(chatType.name())
                .rag_context(ragContext)
                .previous_messages(previousMessages)
                .build();

        return aiWebClient.post()
                .uri("/api/v1/chat/generate")
                .bodyValue(req)
                .retrieve()
                .bodyToMono(AiChatDTO.AiGenerateResponse.class)
                .block();
    }

    private String rewriteQueryIfNeeded(BookAiChatRoom room, String userMsg) {
        try {
            List<Map<String, String>> previousMessages = getPreviousMessages(room);
            if (previousMessages.isEmpty()) {
                return userMsg;
            }

            AiChatDTO.AiRewriteRequest req = AiChatDTO.AiRewriteRequest.builder()
                    .user_msg(userMsg)
                    .previous_messages(previousMessages)
                    .build();

            AiChatDTO.AiRewriteResponse res = aiWebClient.post()
                    .uri("/api/v1/chat/rewrite-query")
                    .bodyValue(req)
                    .retrieve()
                    .bodyToMono(AiChatDTO.AiRewriteResponse.class)
                    .block();

            if (res != null && res.getRewritten_query() != null) {
                log.info("Query rewritten: '{}' -> '{}'", userMsg, res.getRewritten_query());
                return res.getRewritten_query();
            }
        } catch (Exception e) {
            log.warn("Query rewrite failed, using original query. Error: {}", e.getMessage());
        }
        return userMsg;
    }

    /* ========== Helper Methods ========== */

    private User findUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    private Chapter findChapterById(Long chapterId) {
        return chapterRepository.findById(chapterId)
                .orElseThrow(() -> new CustomException(ErrorCode.CHAPTER_NOT_FOUND));
    }

    private BookAiChatRoom findChatRoomById(Long roomId) {
        return chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.AI_CHAT_ROOM_NOT_FOUND));
    }

    private void validateRoomOwner(BookAiChatRoom room, Long userId) {
        if (!room.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.AI_CHAT_NOT_OWNER);
        }
    }

    /**
     * 최근 대화 내역 조회 및 형식 변환 Helper
     */
    private List<Map<String, String>> getPreviousMessages(BookAiChatRoom room) {
        List<BookAiChat> recentChats = chatRepository.findTop5ByChatRoomOrderByCreatedAtDesc(room);

        // 최신순 -> 시간순 정렬
        java.util.Collections.reverse(recentChats);

        List<Map<String, String>> messages = new java.util.ArrayList<>();
        for (BookAiChat chat : recentChats) {
            messages.add(Map.of("role", "user", "content", chat.getUserMessage()));
            messages.add(Map.of("role", "assistant", "content", chat.getAiMessage()));
        }
        return messages;
    }
}
