FROM maven:3-openjdk-17-slim as build
WORKDIR /app
COPY . .
RUN mvn clean package

FROM openjdk:17-slim
COPY --from=build /app/cli/target/mc-releaser-cli-*.jar /app/app.jar
CMD ["java", "-jar", "/app/app.jar"]