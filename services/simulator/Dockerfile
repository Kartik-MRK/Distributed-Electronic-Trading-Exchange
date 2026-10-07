FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /app
COPY . .
RUN ./gradlew :services:simulator:bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=builder /app/services/simulator/build/libs/*.jar app.jar
ENV JAVA_OPTS="-Xms128m -Xmx256m -XX:+UseG1GC -XX:MaxGCPauseMillis=20"
EXPOSE 8089
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
