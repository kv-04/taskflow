# syntax=docker/dockerfile:1

# ---- build ----------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -q dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -q package

# ---- runtime --------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine
# Run as an unprivileged user; data lives on a volume it owns.
RUN addgroup -S -g 10001 app && adduser -S -u 10001 -G app app \
 && mkdir /data && chown app:app /data
COPY --from=build /src/target/taskflow.jar /app/taskflow.jar
USER app
ENV TASKFLOW_HOME=/data
VOLUME /data
# Short-lived CLI: favour start-up time over peak throughput.
ENTRYPOINT ["java", "-XX:+UseSerialGC", "-XX:TieredStopAtLevel=1", "-Xshare:auto", "-jar", "/app/taskflow.jar"]
CMD ["--help"]
