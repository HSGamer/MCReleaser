FROM maven:3-openjdk-17-slim
COPY . .
RUN mvn clean package
RUN mkdir -p /app && cp docker/target/mc-releaser-docker-*.jar /app/app.jar
CMD ["java", "-jar", "/app/app.jar"]