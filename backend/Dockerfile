FROM maven:3.9.11-eclipse-temurin-17-noble AS build
WORKDIR /workspace

COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -q -DskipTests package

FROM ubuntu:24.04 AS tdlib-build
ARG TDLIB_GIT_REPOSITORY=https://github.com/tdlib/td.git
ARG TDLIB_GIT_COMMIT=e0943d068ce90b5010f1aea946e6901e25b43bf6

RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
        ca-certificates \
        cmake \
        g++ \
        git \
        gperf \
        make \
        openjdk-17-jdk-headless \
        php-cli \
        zlib1g-dev \
        libssl-dev \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /tmp/tdlib-src

RUN git init \
    && git remote add origin "${TDLIB_GIT_REPOSITORY}" \
    && git fetch --depth 1 origin "${TDLIB_GIT_COMMIT}" \
    && git checkout --detach FETCH_HEAD \
    && cmake -S . -B build -DCMAKE_BUILD_TYPE=Release -DTD_ENABLE_JNI=ON -DCMAKE_INSTALL_PREFIX=/opt/tdlib-install \
    && cmake --build build --target install -j2

FROM eclipse-temurin:17-jre-noble

RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl libssl3 zlib1g libstdc++6 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

RUN mkdir -p /opt/tdlib
COPY --from=tdlib-build /opt/tdlib-install/bin/libtdjni.so /opt/tdlib/libtdjni.so

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
