FROM maven:3.9.11-eclipse-temurin-17-noble AS build
WORKDIR /workspace

COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre-noble
ARG TDLIB_RELEASE_URL=https://github.com/all4fun/build-tdlib/releases/download/v1.2.1/tdlib-v1.2.1-linux-amd64.tar.gz

RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl tar libssl3 zlib1g libstdc++6 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

RUN mkdir -p /opt/tdlib /tmp/tdlib-download \
    && curl -fsSL "${TDLIB_RELEASE_URL}" -o /tmp/tdlib-download/tdlib.tar.gz \
    && tar -xzf /tmp/tdlib-download/tdlib.tar.gz -C /tmp/tdlib-download \
    && cp /tmp/tdlib-download/tdlib/bin/libtdjni.so /opt/tdlib/libtdjni.so \
    && rm -rf /tmp/tdlib-download

COPY --from=build /workspace/target/*.jar /app/app.jar

RUN useradd --system --create-home --home-dir /app --shell /usr/sbin/nologin appuser \
    && mkdir -p /app/logs /app/tdlib-data/db /app/tdlib-data/files \
    && chown -R appuser:appuser /app /opt/tdlib

ENV LOG_DIR=/app/logs
ENV APP_LOG_FILE=/app/logs/application.log
ENV TDLIB_LOG_FILE=/app/logs/tdlib.log
ENV TDLIB_LIBRARY_PATH=/opt/tdlib/libtdjni.so
ENV LD_LIBRARY_PATH=/opt/tdlib
ENV TDLIB_DATABASE_DIRECTORY=/app/tdlib-data/db
ENV TDLIB_FILES_DIRECTORY=/app/tdlib-data/files

USER appuser
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=5 \
  CMD curl -fsS http://127.0.0.1:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Djava.library.path=/opt/tdlib -jar /app/app.jar"]
