# Runtime-only image: the fat jar is built outside and copied in.
#
# The build is not run here on purpose. :server depends on :core, which is a KMP module with an
# Android target, so configuring it pulls in AGP and needs an Android SDK — a gigabyte of tooling
# this JVM service never uses. Build the jar first:
#
#   ./gradlew :server:buildFatJar
#
FROM eclipse-temurin:21-jre

# Not root: a web-facing process should not be able to write its own image.
RUN useradd --system --create-home --uid 10001 readshelf
USER readshelf
WORKDIR /home/readshelf

COPY --chown=readshelf:readshelf server/build/libs/*-all.jar app.jar

# No EXPOSE and no port baked in: the platform injects PORT and the app reads it.
# JWT_SECRET is never set here — it comes from the platform's secret store at runtime.
ENTRYPOINT ["java", "-jar", "app.jar"]
