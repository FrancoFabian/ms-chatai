# syntax=docker/dockerfile:1

##
## Build stage: compile Spring Boot executable jar with Maven Wrapper
##
FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /workspace

# Copy Maven wrapper and project descriptor first to leverage Docker cache.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw

# Pre-fetch dependencies (faster and more reproducible builds in CI/CD).
RUN ./mvnw -B -ntp -DskipTests dependency:go-offline

# Copy source and package executable jar.
COPY src/ src/
RUN ./mvnw -B -ntp -DskipTests package

##
## Extract stage: split Spring Boot layered jar
## (Spring Boot 4 uses jarmode=tools)
##
FROM eclipse-temurin:25-jdk-alpine AS extract
WORKDIR /workspace

COPY --from=build /workspace/target/ /workspace/target/

RUN set -eux; \
    JAR_FILE="$(find /workspace/target -maxdepth 1 -type f -name '*.jar' | grep -v 'original' | head -n 1)"; \
    test -n "$JAR_FILE"; \
    java -Djarmode=tools -jar "$JAR_FILE" extract --layers --launcher --destination /workspace/layers

##
## Runtime stage: minimal non-root image
##
FROM eclipse-temurin:25-jre-alpine AS runtime
WORKDIR /app

ENV JAVA_OPTS=""
ENV PORT=8080
ENV SERVER_PORT=8080

# Create non-root user with fixed uid/gid.
RUN addgroup -S spring -g 10001 \
    && adduser -S spring -u 10001 -G spring

# Copy layers in recommended order for optimal cache reuse.
COPY --from=extract --chown=spring:spring /workspace/layers/dependencies/ ./
COPY --from=extract --chown=spring:spring /workspace/layers/spring-boot-loader/ ./
COPY --from=extract --chown=spring:spring /workspace/layers/snapshot-dependencies/ ./
COPY --from=extract --chown=spring:spring /workspace/layers/application/ ./

USER spring:spring
EXPOSE 8080

# Supports runtime JVM tuning via JAVA_OPTS.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
