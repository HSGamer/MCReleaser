FROM java:17
RUN apt-get update
RUN apt-get install -y maven

COPY . .
RUN mvn clean package
RUN mkdir -p /app && cp docker/target/mc-releaser-docker-*.jar /app/app.jar
CMD ["java", "-jar", "/app/app.jar"]