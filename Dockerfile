# syntax=docker/dockerfile:1

# ---- Build: compile the application with the full JDK and Maven ----
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# Dependencies first, so they are cached until pom.xml changes.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -q package -DskipTests && cp target/HelpDesk-*.jar app.jar

# ---- Run: only a JRE and the jar, as an unprivileged user ----
FROM eclipse-temurin:25-jre
WORKDIR /app

RUN groupadd --system helpdesk && useradd --system --gid helpdesk --no-create-home helpdesk
COPY --from=build --chown=helpdesk:helpdesk /workspace/app.jar app.jar
USER helpdesk

# Size the heap from the container's memory limit, so the app fits small
# free-tier instances, and restart cleanly rather than limp on after running
# out of memory.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k -XX:+ExitOnOutOfMemoryError"

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
