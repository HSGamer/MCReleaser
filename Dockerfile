FROM maven:3-openjdk-17-slim as build
WORKDIR /app
COPY . /app
RUN mvn clean package

FROM openjdk:17-slim
WORKDIR /app
COPY --from=build /app/docker/target/mc-releaser-docker-*.jar /app/app.jar
VOLUME /app/primary
VOLUME /app/secondary
CMD ["java", "-jar", "/app/app.jar"]