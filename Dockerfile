# Multi-stage build: compiles the fat jar with Maven, then runs it on a minimal JRE image.

# Build stage: full JDK + Maven, discarded from the final image.
FROM maven:3.9-eclipse-temurin-24 AS build
# Working directory for the build stage.
WORKDIR /build
# Copy only the POM first, so dependency resolution is cached separately from source changes.
COPY pom.xml .
# Download dependencies into their own cached layer, quietly and non-interactively.
RUN mvn -q -B dependency:go-offline
# Copy source last, so only this layer rebuilds when code changes.
COPY src ./src
# Build the runnable fat jar; tests already ran in CI, no need to repeat them here.
RUN mvn -q -B clean package -DskipTests

# Runtime stage: JRE only, much smaller than the build image.
FROM eclipse-temurin:24-jre-alpine
# Create a dedicated non-root user/group.
RUN addgroup -S app && adduser -S app -G app
# Run the container as that user, not root.
USER app
# Working directory for the runtime stage.
WORKDIR /app
# Copy only the built jar from the build stage, nothing else.
COPY --from=build /build/target/*.jar app.jar
# Document the port the app listens on (informational, doesn't publish it).
EXPOSE 8080
# Start the application when the container runs.
ENTRYPOINT ["java", "-jar", "app.jar"]
