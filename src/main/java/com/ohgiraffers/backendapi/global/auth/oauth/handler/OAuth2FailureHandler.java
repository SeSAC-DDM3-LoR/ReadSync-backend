package com.ohgiraffers.backendapi.global.auth.oauth.handler;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class OAuth2FailureHandler extends SimpleUrlAuthenticationFailureHandler {

    @Value("${frontend.url}")
    private String frontendUrl;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException, ServletException {

        log.error("OAuth2 로그인 실패: {}", exception.getMessage());

        String errorType = "UNKNOWN_ERROR";
        String errorMessage = "로그인 중 오류가 발생했습니다.";

        // OAuth2AuthenticationException의 경우 OAuth2Error에서 에러 코드 추출
        if (exception instanceof org.springframework.security.oauth2.core.OAuth2AuthenticationException) {
            org.springframework.security.oauth2.core.OAuth2AuthenticationException oauthException = (org.springframework.security.oauth2.core.OAuth2AuthenticationException) exception;
            String errorCode = oauthException.getError().getErrorCode();

            if ("SUSPENDED_USER".equals(errorCode)) {
                errorType = "SUSPENDED_USER";
                errorMessage = "정지된 계정입니다.";
            } else if ("WITHDRAWN_USER".equals(errorCode)) {
                errorType = "WITHDRAWN_USER";
                errorMessage = "탈퇴한 계정입니다.";
            }
        }

        // 프론트엔드 콜백 페이지로 에러 파라미터와 함께 리다이렉트
        // 한글 메시지는 반드시 URL 인코딩 필요 (Tomcat 헤더 제약)
        String encodedErrorMessage = URLEncoder.encode(errorMessage, StandardCharsets.UTF_8);

        String targetUrl = UriComponentsBuilder.fromUriString(frontendUrl + "/oauth/callback")
                .queryParam("error", errorType)
                .queryParam("errorMessage", encodedErrorMessage)
                .build().toUriString();

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}
