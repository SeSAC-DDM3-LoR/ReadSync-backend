package com.ohgiraffers.backendapi.global.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WebController {

    // 루트 경로 접속 시 웰컴 메시지 출력 (EB Health Check 및 접속 테스트용)
    @GetMapping("/")
    public String home() {
        return "ReadSync API Server is running! 🚀 (Health Check OK)";
    }

    // 파비콘 요청 시 빈 응답 반환 (500 에러 방지)
    @GetMapping("/favicon.ico")
    public void favicon() {
    }
}
