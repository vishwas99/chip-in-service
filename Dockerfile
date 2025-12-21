# -------- Build stage --------
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /app

# Install Maven
RUN apt-get update && \
    apt-get install -y maven && \
    rm -rf /var/lib/apt/lists/*

# Copy pom first (better caching)
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

# Copy source
COPY src src

# Build jar
RUN mvn -B -q package -DskipTests


# -------- Runtime stage --------
FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
