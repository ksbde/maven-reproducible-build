# maven:3.8.6-openjdk-11
FROM maven@sha256:805f366910aea2a91ed263654d23df58bd239f218b2f9562ff51305be81fa215 AS builder

WORKDIR /app

COPY . .

RUN mvn clean package


#openjdk:11-jdk-slim-sid
FROM openjdk@sha256:f478bbca4a3616cc8abebf9951a0b3cd08d0e010626e08ee1d73eeb36a33e765

WORKDIR /app
COPY --from=builder /app/target/*-jar-with-dependencies.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
