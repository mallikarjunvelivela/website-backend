# Build stage
FROM gradle:jdk21 AS build
WORKDIR /app
COPY . .
RUN gradle bootJar

# Package stage
FROM openjdk:21-slim
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 9009
ENTRYPOINT ["java", "-jar", "app.jar"]
