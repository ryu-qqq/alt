# 멀티스테이지 빌드 — bootstrap-api 진입점
# - builder: Gradle wrapper로 bootJar 생성
# - runtime: JRE only, non-root user, Spring Boot fat jar 실행
# - 헬스체크는 ECS task definition의 healthCheck로 위임 (curl 미설치)

# ────────────────────────────────────────────────────────────
# Stage 1: builder
# ────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /app

# Gradle wrapper + 루트 빌드 스크립트만 먼저 복사 → 의존성 레이어 캐시 극대화
COPY gradlew gradlew.bat settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle gradle

# 모듈 build script만 복사 (의존성 prefetch 캐시용)
COPY domain/build.gradle.kts domain/
COPY application/build.gradle.kts application/
COPY adapter-in/build.gradle.kts adapter-in/
COPY adapter-out/persistence-mysql/build.gradle.kts adapter-out/persistence-mysql/
COPY adapter-out/cache-caffeine/build.gradle.kts adapter-out/cache-caffeine/
COPY adapter-out/client-csrng/build.gradle.kts adapter-out/client-csrng/
COPY adapter-out/client-llm/build.gradle.kts adapter-out/client-llm/
COPY bootstrap/bootstrap-api/build.gradle.kts bootstrap/bootstrap-api/

# 의존성 prefetch (best-effort — 일부 task는 src 없으면 실패 가능, 무시)
RUN ./gradlew :bootstrap:bootstrap-api:dependencies --no-daemon || true

# 소스 복사
COPY domain/src domain/src
COPY application/src application/src
COPY adapter-in/src adapter-in/src
COPY adapter-out/persistence-mysql/src adapter-out/persistence-mysql/src
COPY adapter-out/cache-caffeine/src adapter-out/cache-caffeine/src
COPY adapter-out/client-csrng/src adapter-out/client-csrng/src
COPY adapter-out/client-llm/src adapter-out/client-llm/src
COPY bootstrap/bootstrap-api/src bootstrap/bootstrap-api/src

# bootJar만 생성 (테스트는 CI에서 별도 실행)
RUN ./gradlew :bootstrap:bootstrap-api:bootJar --no-daemon -x test

# ────────────────────────────────────────────────────────────
# Stage 2: runtime
# ────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# non-root
RUN groupadd --system app && useradd --system --gid app --home /app app

COPY --from=builder --chown=app:app /app/bootstrap/bootstrap-api/build/libs/*.jar app.jar

USER app

EXPOSE 8080

# JVM 메모리: 컨테이너 메모리의 75%를 heap으로
# G1GC 명시: jdk21 기본이지만 가독성
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
