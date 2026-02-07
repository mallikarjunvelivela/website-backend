# Build stage
FROM gradle:jdk21 AS build
WORKDIR /app
COPY . .
RUN gradle bootJar

# Package stage
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 9009
ENTRYPOINT ["sh", "-c", "java -jar app.jar --server.port=${PORT:-9009}"]
