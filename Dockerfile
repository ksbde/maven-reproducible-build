FROM maven:3.8.6-openjdk-11 AS builder

WORKDIR /app

COPY . .

RUN mvn clean package

FROM openjdk:11-jdk-slim

WORKDIR /app
COPY --from=builder /app/target/*-jar-with-dependencies.jar app.jar

#EXPOSE 8080
#
#ENTRYPOINT ["java", "-jar", "app.jar"]
