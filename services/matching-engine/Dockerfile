FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /app
COPY . .
RUN ./gradlew :services:matching-engine:bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=builder /app/services/matching-engine/build/libs/*.jar app.jar
ENV JAVA_OPTS="-Xms256m -Xmx384m -XX:+UseG1GC -XX:MaxGCPauseMillis=10"
EXPOSE 8084
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
