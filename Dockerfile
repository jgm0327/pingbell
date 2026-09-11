# syntax=docker/dockerfile:1

# ---- Build stage ----
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace

# Resolve the Gradle wrapper and dependencies in their own layer so editing src/ doesn't bust
# the dependency-download cache.
COPY gradlew gradlew.bat ./
COPY gradle gradle
COPY build.gradle settings.gradle ./
RUN chmod +x gradlew && ./gradlew --no-daemon help

COPY src src
RUN ./gradlew --no-daemon bootJar -x test

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN groupadd --system pingbell && useradd --system --gid pingbell pingbell
COPY --from=build /workspace/build/libs/*.jar app.jar
USER pingbell

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
