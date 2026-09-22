# syntax=docker/dockerfile:1

# =========================================================
# 1. Build Stage
# =========================================================

FROM eclipse-temurin:25-jdk AS builder

WORKDIR /app

# Gradle Wrapper 복사 및 실행 권한 설정
COPY --chmod=0755 gradlew gradlew
COPY gradle/ gradle/

# 상대적으로 변경 빈도가 낮은 Build 설정 먼저 복사
COPY settings.gradle build.gradle ./

# 자주 변경되는 Source Code는 뒤쪽에 복사
COPY src/ src/

# Gradle Cache를 재사용하며 Spring Boot JAR 생성
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew bootJar --no-daemon

# =========================================================
# 2. Runtime Stage
# =========================================================

FROM eclipse-temurin:25 AS runtime

WORKDIR /app

# Application 전용 non-root 사용자 생성
RUN groupadd --system app \
    && useradd --system --gid app app

# Build Stage에서 생성된 JAR만 복사
COPY --from=builder --chown=app:app \
    /app/build/libs/app.jar app.jar

# Application을 non-root 사용자로 실행
USER app

# Spring Boot Application Port
EXPOSE 8080

# Application 실행
ENTRYPOINT ["java", "-jar", "app.jar"]