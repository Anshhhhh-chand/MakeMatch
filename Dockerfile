# Build stage: dependencies are resolved in their own layer
FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q package -DskipTests

# Runtime stage: JRE 17 only, non-root user
FROM eclipse-temurin:17-jre
WORKDIR /app
RUN useradd --system --no-create-home app
COPY --from=build /workspace/target/game-match-service-*.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
