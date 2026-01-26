# -------------------------------------------------------------------
# 1. 빌드 단계 (Builder Stage)
# -------------------------------------------------------------------
# Gradle과 Java 21이 포함된 이미지를 사용하여 빌드를 수행합니다.
FROM gradle:8.5-jdk21-alpine AS builder

WORKDIR /app

# 의존성 캐싱을 위해 설정 파일만 먼저 복사
COPY build.gradle settings.gradle ./
# 라이브러리 다운로드 (소스코드 복사 전)
RUN gradle dependencies --no-daemon || return 0

# 전체 소스 복사 및 빌드 (테스트 제외하여 속도 향상)
COPY . .
RUN gradle clean build -x test --no-daemon

# -------------------------------------------------------------------
# 2. 실행 단계 (Run Stage)
# -------------------------------------------------------------------
# 실제 실행 시에는 가벼운 JDK 이미지만 필요합니다.
FROM amazoncorretto:21-alpine-jdk

WORKDIR /app

# 빌드 단계에서 생성된 JAR 파일만 쏙 가져옵니다.
# (경로가 정확한지 확인 필요, 보통 build/libs에 생성됨)
COPY --from=builder /app/build/libs/*.jar app.jar

# 실행
ENTRYPOINT ["java", "-jar", "app.jar"]
