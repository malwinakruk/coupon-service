# Packages the already-built fat jar into a minimal JRE image. Run `mvn clean package`
# (or `-DskipTests`) before building this image — it does not compile anything itself.

# Runtime stage: JRE only, no build tooling.
FROM eclipse-temurin:24-jre-alpine
# Create a dedicated non-root user/group.
RUN addgroup -S app && adduser -S app -G app
# Run the container as that user, not root.
USER app
# Working directory for the runtime stage.
WORKDIR /app
# Copy the fat jar (excludes the plain .jar.original Spring Boot's repackage step leaves behind).
COPY target/*.jar app.jar
# Document the port the app listens on (informational, doesn't publish it).
EXPOSE 8080
# Start the application when the container runs.
ENTRYPOINT ["java", "-jar", "app.jar"]
