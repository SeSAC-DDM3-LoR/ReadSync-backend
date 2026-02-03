# 빌드 단계 생략, 실행 환경만 설정
FROM amazoncorretto:21-alpine-jdk

WORKDIR /app

# GitHub Actions에서 복사해온 JAR 파일을 컨테이너로 복사
COPY *.jar app.jar

# 실행
ENTRYPOINT ["java", "-jar", "app.jar"]