package com.ohgiraffers.backendapi.domain.level.controller;

import com.ohgiraffers.backendapi.domain.level.dto.LevelResponse;
import com.ohgiraffers.backendapi.domain.level.service.LevelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/levels")
@RequiredArgsConstructor
@Tag(name = "Level", description = "레벨 관련 API")
public class LevelController {

    private final LevelService levelService;

    @GetMapping
    @Operation(summary = "모든 레벨 정보 조회", description = "시스템에 등록된 모든 레벨 정보를 조회합니다.")
    public ResponseEntity<List<LevelResponse>> getAllLevels() {
        List<LevelResponse> levels = levelService.getAllLevels();
        return ResponseEntity.ok(levels);
    }

    @GetMapping("/{levelId}")
    @Operation(summary = "특정 레벨 정보 조회", description = "특정 레벨의 상세 정보를 조회합니다.")
    public ResponseEntity<LevelResponse> getLevelById(@PathVariable Long levelId) {
        LevelResponse level = levelService.getLevelById(levelId);
        return ResponseEntity.ok(level);
    }

    @GetMapping("/by-exp")
    @Operation(summary = "경험치로 레벨 조회", description = "주어진 경험치로 달성 가능한 레벨을 조회합니다.")
    public ResponseEntity<LevelResponse> getLevelByExperience(@RequestParam int experience) {
        LevelResponse level = levelService.getLevelByExperience(experience);
        return ResponseEntity.ok(level);
    }
}
