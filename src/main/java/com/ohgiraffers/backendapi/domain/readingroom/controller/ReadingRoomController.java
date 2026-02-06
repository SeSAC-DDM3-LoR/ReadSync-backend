package com.ohgiraffers.backendapi.domain.readingroom.controller;

import com.ohgiraffers.backendapi.domain.readingroom.dto.CreateRoomRequest;
import com.ohgiraffers.backendapi.domain.readingroom.service.ReadingRoomService;
import com.ohgiraffers.backendapi.global.common.annotation.CurrentUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.List;
import java.util.Map;

@Slf4j
@Tag(name = "Reading Room", description = "TTS 독서룸 관련 API")
@RestController
@RequestMapping("/v1/reading-rooms")
@RequiredArgsConstructor
public class ReadingRoomController {

    private final ReadingRoomService readingRoomService;

    /**
     * WebSocket: 문단 싱크 메시지 처리
     * 방장이 문단을 변경하면 해당 문단의 TTS를 생성하여 방 참여자들에게 브로드캐스트
     */
    @MessageMapping("/room/sync")
    public void handleRoomSync(@Payload Map<String, Object> message, Principal principal) {
        Long roomId = ((Number) message.get("roomId")).longValue();
        String type = (String) message.get("type");
        String paragraphId = (String) message.get("paragraphId");

        log.info("[WebSocket] Room sync received - roomId: {}, type: {}, paragraphId: {}", roomId, type, paragraphId);

        if ("SYNC_PARAGRAPH".equals(type) && paragraphId != null) {
            // 인증된 사용자 ID (방장인지 확인은 Service에서)
            Long userId = principal != null ? Long.parseLong(principal.getName()) : null;

            // 해당 문단의 TTS 생성 및 브로드캐스트
            readingRoomService.playParagraph(roomId, userId, paragraphId);
        }
    }

    @Operation(summary = "독서룸 생성")
    @PostMapping
    public ResponseEntity<Long> createRoom(
            @Parameter(hidden = true) @CurrentUserId Long userId,
            @RequestBody CreateRoomRequest roomRequest) {
        Long roomId = readingRoomService.createRoom(userId, roomRequest);
        return ResponseEntity.ok(roomId);
    }

    @Operation(summary = "독서룸 입장")
    @PostMapping("/{roomId}/enter")
    public ResponseEntity<Void> enterRoom(
            @Parameter(hidden = true) @CurrentUserId Long userId,
            @PathVariable Long roomId) {
        readingRoomService.enterRoom(roomId, userId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "독서룸 퇴장", description = "참여자가 독서룸에서 나갑니다. (방장이 나가면 방 종료)")
    @DeleteMapping("/{roomId}/leave")
    public ResponseEntity<Void> leaveRoom(
            @Parameter(hidden = true) @CurrentUserId Long userId,
            @PathVariable Long roomId) {

        readingRoomService.leaveRoom(roomId, userId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "참여자 강퇴 (방장 전용)", description = "방장이 특정 참여자를 강제로 퇴장시킵니다.")
    @DeleteMapping("/{roomId}/kick/{targetUserId}")
    public ResponseEntity<Void> kickUser(
            @Parameter(hidden = true) @CurrentUserId Long hostId,
            @PathVariable Long roomId,
            @PathVariable Long targetUserId) {

        readingRoomService.kickUser(roomId, hostId, targetUserId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "재생 속도 변경 (방장 전용)")
    @PatchMapping("/{roomId}/speed")
    public ResponseEntity<Void> updatePlaySpeed(
            @Parameter(hidden = true) @CurrentUserId Long hostId,
            @PathVariable Long roomId,
            @RequestParam BigDecimal speed) {

        readingRoomService.updatePlaySpeed(roomId, hostId, speed);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "독서 시작 (방장 전용)", description = "상태를 PLAYING으로 변경하고 입장을 제한합니다.")
    @PatchMapping("/{roomId}/start")
    public ResponseEntity<Void> startReading(
            @Parameter(hidden = true) @CurrentUserId Long hostId,
            @PathVariable Long roomId) {

        readingRoomService.startReading(roomId, hostId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "독서 일시정지/재개 (방장 전용)", description = "PLAYING과 PAUSED 상태를 토글합니다.")
    @PatchMapping("/{roomId}/pause")
    public ResponseEntity<Void> pauseReading(
            @Parameter(hidden = true) @CurrentUserId Long hostId,
            @PathVariable Long roomId) {

        readingRoomService.pauseReading(roomId, hostId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "독서 종료 및 목표 달성 (방장 전용)", description = "독서를 종료하고 참여자들에게 경험치를 지급합니다.")
    @PatchMapping("/{roomId}/finish")
    public ResponseEntity<Void> finishReading(
            @Parameter(hidden = true) @CurrentUserId Long hostId,
            @PathVariable Long roomId) {

        readingRoomService.finishReading(roomId, hostId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "독서룸 목록 조회", description = "현재 활성화된 모든 독서룸 목록을 조회합니다.")
    @GetMapping
    public ResponseEntity<java.util.List<com.ohgiraffers.backendapi.domain.readingroom.dto.ReadingRoomResponse>> getRooms() {
        java.util.List<com.ohgiraffers.backendapi.domain.readingroom.dto.ReadingRoomResponse> rooms = readingRoomService
                .getAllActiveRooms();
        return ResponseEntity.ok(rooms);
    }

    @Operation(summary = "독서룸 상세 조회", description = "특정 독서룸의 상세 정보를 조회합니다.")
    @GetMapping("/{roomId}")
    public ResponseEntity<com.ohgiraffers.backendapi.domain.readingroom.dto.ReadingRoomResponse> getRoom(
            @PathVariable Long roomId) {
        com.ohgiraffers.backendapi.domain.readingroom.dto.ReadingRoomResponse room = readingRoomService
                .getRoomDetail(roomId);
        return ResponseEntity.ok(room);
    }

    @Operation(summary = "참여자 목록 조회", description = "특정 독서룸의 참여자 목록을 조회합니다.")
    @GetMapping("/{roomId}/participants")
    public ResponseEntity<java.util.List<com.ohgiraffers.backendapi.domain.readingroom.dto.ParticipantResponse>> getParticipants(
            @PathVariable Long roomId) {
        java.util.List<com.ohgiraffers.backendapi.domain.readingroom.dto.ParticipantResponse> participants = readingRoomService
                .getParticipants(roomId);
        return ResponseEntity.ok(participants);
    }

}
