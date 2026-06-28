FROM ubuntu:24.04 AS tdlib-build
ARG TDLIB_GIT_REPOSITORY=https://github.com/tdlib/td.git
ARG TDLIB_GIT_COMMIT=e0943d068ce90b5010f1aea946e6901e25b43bf6
ARG TDLIB_BUILD_JOBS=4

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
    && cmake --build build --target install -j"${TDLIB_BUILD_JOBS}"

RUN rm -rf example/java/td build-example-java \
    && ln -s /opt/tdlib-install example/java/td \
    && cmake -S example/java -B build-example-java -DCMAKE_BUILD_TYPE=Release -DCMAKE_INSTALL_PREFIX=/opt/tdjni-install \
    && cmake --build build-example-java --target install -j"${TDLIB_BUILD_JOBS}" \
    && test -f /opt/tdjni-install/bin/libtdjni.so

FROM scratch
COPY --from=tdlib-build /opt/tdjni-install/bin/libtdjni.so /opt/tdlib/libtdjni.so
