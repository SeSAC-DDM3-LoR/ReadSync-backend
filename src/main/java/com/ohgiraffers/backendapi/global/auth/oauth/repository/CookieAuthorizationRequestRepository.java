package com.ohgiraffers.backendapi.global.auth.oauth.repository;

import lombok.extern.slf4j.Slf4j;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.SerializationUtils;

import java.util.Base64;

/**
 * Cookie 기반 OAuth2 Authorization Request 저장소
 * STATELESS 세션 정책에서 OAuth2 state를 유지하기 위해 사용
 */
@Slf4j
@Component
public class CookieAuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    public static final String OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME = "oauth2_auth_request";
    public static final int COOKIE_EXPIRE_SECONDS = 180; // 3분

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        log.info("OAuth2 Authorization Request 로드 시도 - URI: {}", request.getRequestURI());

        // 모든 쿠키 로그
        Cookie[] cookies = request.getCookies();
        log.info("전체 쿠키 개수: {}", cookies != null ? cookies.length : 0);
        if (cookies != null) {
            for (Cookie c : cookies) {
                log.info("  - 쿠키: name={}, valueLength={}", c.getName(),
                        c.getValue() != null ? c.getValue().length() : 0);
            }
        }

        OAuth2AuthorizationRequest authRequest = getCookie(request, OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME)
                .map(this::deserialize)
                .orElse(null);

        if (authRequest != null) {
            log.info("OAuth2 쿠키에서 Authorization Request 찾음: state={}", authRequest.getState());
        } else {
            log.warn("OAuth2 Authorization Request 쿠키를 찾을 수 없음!");
        }
        return authRequest;
    }

    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest,
            HttpServletRequest request,
            HttpServletResponse response) {
        if (authorizationRequest == null) {
            log.debug("Authorization Request is null. Deleting cookie.");
            deleteCookie(request, response, OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME);
            return;
        }

        log.info("Saving Authorization Request to Cookie. state={}", authorizationRequest.getState());
        String serialized = serialize(authorizationRequest);
        log.debug("Serialized cookie value length: {}", serialized.length());

        ResponseCookie cookie = ResponseCookie.from(OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME, serialized)
                .path("/")
                .httpOnly(true)
                .secure(true) // HTTPS Required for SameSite=None
                .sameSite("None")
                .maxAge(COOKIE_EXPIRE_SECONDS)
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        log.info("OAuth2 쿠키 저장 완료: state={}", authorizationRequest.getState());
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request,
            HttpServletResponse response) {
        log.debug("Removing Authorization Request Cookie");
        OAuth2AuthorizationRequest authorizationRequest = loadAuthorizationRequest(request);
        deleteCookie(request, response, OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME);
        return authorizationRequest;
    }

    private java.util.Optional<Cookie> getCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null && cookies.length > 0) {
            for (Cookie cookie : cookies) {
                if (name.equals(cookie.getName())) {
                    return java.util.Optional.of(cookie);
                }
            }
        }
        return java.util.Optional.empty();
    }

    private void deleteCookie(HttpServletRequest request, HttpServletResponse response, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null && cookies.length > 0) {
            for (Cookie cookie : cookies) {
                if (name.equals(cookie.getName())) {
                    ResponseCookie deleteCookie = ResponseCookie.from(name, "")
                            .path("/")
                            .httpOnly(true)
                            .secure(true)
                            .sameSite("None")
                            .maxAge(0)
                            .build();
                    response.addHeader(HttpHeaders.SET_COOKIE, deleteCookie.toString());
                }
            }
        }
    }

    private String serialize(OAuth2AuthorizationRequest authorizationRequest) {
        return Base64.getUrlEncoder().encodeToString(
                SerializationUtils.serialize(authorizationRequest));
    }

    private OAuth2AuthorizationRequest deserialize(Cookie cookie) {
        return (OAuth2AuthorizationRequest) SerializationUtils.deserialize(
                Base64.getUrlDecoder().decode(cookie.getValue()));
    }
}
