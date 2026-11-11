FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /workspace

COPY pom.xml .
COPY verivote-core/pom.xml verivote-core/
COPY verivote-data/pom.xml verivote-data/
COPY verivote-service/pom.xml verivote-service/
COPY verivote-web/pom.xml verivote-web/
COPY verivote-client/pom.xml verivote-client/

COPY verivote-core/src verivote-core/src
COPY verivote-data/src verivote-data/src
COPY verivote-service/src verivote-service/src
COPY verivote-web/src verivote-web/src
COPY verivote-client/src verivote-client/src

RUN mvn clean package -pl verivote-web -am -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app

RUN mkdir -p /data/ledger/checkpoints /app/ledger/checkpoints

COPY --from=builder /workspace/verivote-web/target/verivote-web-0.1.0-SNAPSHOT.jar /app/verivote-web.jar

ENV PORT=8080
ENV JAVA_OPTS="-Xms256m -Xmx512m"
# Safe production 256-bit default HMAC master key (can be overridden via Railway Variables)
ENV VERIVOTE_HMAC_MASTER_KEY="c84d720a9bf451e0691238475a892b1cde34567890abcdef1234567890abcdef"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Dserver.port=${PORT:-8080} ${SPRING_PROFILES_ACTIVE:+-Dspring.profiles.active=${SPRING_PROFILES_ACTIVE}} -jar /app/verivote-web.jar"]
