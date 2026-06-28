ARG TDLIB_IMAGE=neuroinfogrinder-tdlib:e0943d068ce90b5010f1aea946e6901e25b43bf6

FROM ${TDLIB_IMAGE} AS tdlib

FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN mkdir -p /opt/tdlib /app/logs /app/tdlib-data/db /app/tdlib-data/files
COPY --from=tdlib /opt/tdlib/libtdjni.so /opt/tdlib/libtdjni.so
COPY --from=build /workspace/target/neuroinfogrinder-backend-2-0.1.0-SNAPSHOT.jar /app/backend-2.jar
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Djava.library.path=/opt/tdlib -jar /app/backend-2.jar"]
