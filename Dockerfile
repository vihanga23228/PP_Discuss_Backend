# syntax=docker/dockerfile:1

# --- build stage ------------------------------------------------------------
# Full JDK, only used to produce the jar. Nothing here ships to production.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Wrapper and build scripts first: as long as these do not change, Docker reuses
# the cached dependency download instead of refetching it on every deploy.
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies

COPY src ./src
# bootJar, not build: skips the tests and the extra -plain.jar we do not run.
RUN ./gradlew --no-daemon bootJar

# --- runtime stage ----------------------------------------------------------
# JRE only, so the deployed image has no compiler or Gradle cache in it.
FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /app/build/libs/*-SNAPSHOT.jar app.jar

# Where the importer writes page renders and cropped figures (app.media.dir).
# On a host with an ephemeral filesystem this is wiped on every redeploy unless
# a persistent disk is mounted here.
RUN mkdir -p /app/media && useradd --system --uid 1001 spring && chown -R spring /app
USER spring

# Local default. Render (and most hosts) inject their own PORT, which
# application.properties reads as ${PORT:8083}.
EXPOSE 8083

# MaxRAMPercentage keeps the heap inside the container limit rather than the
# host's memory, which matters on small instances.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
