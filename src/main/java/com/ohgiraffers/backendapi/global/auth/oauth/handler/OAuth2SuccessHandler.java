package com.ohgiraffers.backendapi.global.auth.oauth.handler;

import com.ohgiraffers.backendapi.domain.user.entity.RefreshToken;
import com.ohgiraffers.backendapi.domain.user.entity.User;
import com.ohgiraffers.backendapi.domain.user.repository.RefreshTokenRepository;
import com.ohgiraffers.backendapi.domain.user.repository.UserRepository;
import com.ohgiraffers.backendapi.global.auth.jwt.JwtTokenProvider;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

        private final JwtTokenProvider jwtTokenProvider;
        private final UserRepository userRepository;
        private final RefreshTokenRepository refreshTokenRepository;

        @Value("${frontend.url}")
        private String frontendUrl;

        @Override
        public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                        Authentication authentication) throws IOException, ServletException {
                // 1. 로그인된 유저 정보 가져오기
                OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
                Map<String, Object> attributes = oAuth2User.getAttributes();

                String loginId = (String) attributes.get("loginId");

                log.info("로그인 성공! Target Login ID: {}", loginId);

                // 2. DB에서 유저 조회 (loginId가 정확하므로 무조건 성공)
                User user = userRepository.findByLoginId(loginId)
                                .orElseThrow(() -> new RuntimeException("User not found after OAuth2 login"));

                // 3. 토큰 발급
                String accessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getRole());
                String refreshToken = jwtTokenProvider.createRefreshToken(user.getId());

                // 4. 리프레시 토큰 저장 (기존 토큰 있으면 업데이트, 없으면 생성)
                RefreshToken existingToken = refreshTokenRepository.findByUserId(user.getId())
                                .orElse(null);

                if (existingToken != null) {
                        existingToken.updateToken(refreshToken);
                        refreshTokenRepository.save(existingToken);
                } else {
                        refreshTokenRepository.save(RefreshToken.builder()
                                        .userId(user.getId())
                                        .token(refreshToken)
                                        .build());
                }

                // 5. 리다이렉트 (프론트엔드 Callback 페이지로 이동)
                // isNewUser 판별: CustomOAuth2UserService에서 넣어준 attributes 확인
                boolean isNewUser = Boolean.TRUE.equals(attributes.get("isNewUser"));

                String targetUrl = UriComponentsBuilder.fromUriString(frontendUrl + "/oauth/callback")
                                .queryParam("accessToken", accessToken)
                                .queryParam("refreshToken", refreshToken)
                                .queryParam("isNewUser", isNewUser)
                                .build().toUriString();

                getRedirectStrategy().sendRedirect(request, response, targetUrl);
        }
}