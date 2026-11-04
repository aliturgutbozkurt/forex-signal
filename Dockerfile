# syntax=docker/dockerfile:1.7
# Stage 1: Build & AOT/CDS Cache Generation (Java 25 LTS, JEP 514/515)
FROM eclipse-temurin:25-jdk-Noble AS builder
WORKDIR /workspace
COPY . .
RUN ./mvnw -B package -DskipTests && \
    java -Djarmode=tools -jar target/forex-signal-platform-1.0.0-SNAPSHOT.jar extract --destination application && \
    cd application && \
    java -XX:AOTMode=record -XX:AOTConfiguration=app.aotconf -Dspring.context.exit=onRefresh -jar forex-signal-platform-1.0.0-SNAPSHOT.jar && \
    java -XX:AOTMode=create -XX:AOTConfiguration=app.aotconf -XX:AOTCache=app.aot -jar forex-signal-platform-1.0.0-SNAPSHOT.jar

# Stage 2: Distroless Java 25 Runtime (< 1.5s startup with AOT cache & Compact Object Headers JEP 519)
FROM gcr.io/distroless/java25-debian12:nonroot
WORKDIR /app
COPY --from=builder /workspace/application/ ./
EXPOSE 8080
ENTRYPOINT ["java", "-XX:+UseCompactObjectHeaders", "-XX:AOTCache=app.aot", "-jar", "forex-signal-platform-1.0.0-SNAPSHOT.jar"]

