FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates \
    && rm -rf /var/lib/apt/lists/*

COPY certs/russian-trusted-root-ca.crt /usr/local/share/ca-certificates/russian-trusted-root-ca.crt
COPY certs/russian-trusted-sub-ca.crt /usr/local/share/ca-certificates/russian-trusted-sub-ca.crt

RUN update-ca-certificates \
    && keytool -importcert -noprompt -storepass changeit -cacerts \
       -alias russian-trusted-root-ca \
       -file /usr/local/share/ca-certificates/russian-trusted-root-ca.crt \
    && keytool -importcert -noprompt -storepass changeit -cacerts \
       -alias russian-trusted-sub-ca \
       -file /usr/local/share/ca-certificates/russian-trusted-sub-ca.crt

COPY --from=build /app/target/max-helpdesk-bot-1.0.0.jar app.jar

RUN mkdir -p /data

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
