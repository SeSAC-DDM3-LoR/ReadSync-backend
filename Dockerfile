FROM amazoncorretto:21-alpine-jdk
WORKDIR /app
# 빌드된 JAR 파일 하나만 복사 (파일명을 명시하는 것이 안전합니다)
COPY *.jar app.jar
# Beanstalk Docker 플랫폼은 기본적으로 8080 포트를 기대하는 경우가 많습니다.
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]