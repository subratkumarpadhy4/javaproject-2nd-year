# Stage 1: Build all modules
FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /workspace

# Copy POM files first for dependency caching
COPY pom.xml .
COPY verivote-core/pom.xml verivote-core/
COPY verivote-data/pom.xml verivote-data/
COPY verivote-service/pom.xml verivote-service/
COPY verivote-web/pom.xml verivote-web/
COPY verivote-client/pom.xml verivote-client/

# Copy all source code
COPY verivote-core/src verivote-core/src
COPY verivote-data/src verivote-data/src
COPY verivote-service/src verivote-service/src
COPY verivote-web/src verivote-web/src
COPY verivote-client/src verivote-client/src

# Build executable package
RUN mvn clean package -DskipTests

# Stage 2: Minimal runtime image
FROM eclipse-temurin:21-jre
WORKDIR /app

# Create ledger directories for append-only blocks and external checkpoints
RUN mkdir -p /app/ledger/checkpoints

# Copy executable fat JAR from builder stage
COPY --from=builder /workspace/verivote-web/target/verivote-web-*.jar /app/verivote-web.jar

ENV PORT=8080
ENV JAVA_OPTS="-Xms256m -Xmx512m"

EXPOSE 8080

VOLUME ["/app/ledger"]

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/verivote-web.jar"]
