FROM maven:3-eclipse-temurin-17-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY cli/pom.xml cli/
COPY core/pom.xml core/
COPY github/pom.xml github/
COPY hangar/pom.xml hangar/
COPY modrinth/pom.xml modrinth/
COPY polymart/pom.xml polymart/
COPY renderer/pom.xml renderer/
COPY version/pom.xml version/
RUN mvn dependency:go-offline -B
COPY . .
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
COPY --from=build /app/cli/target/mc-releaser-cli-*.jar /app/app.jar
COPY --from=build /app/cli/target/lib /app/lib
CMD ["java", "-jar", "/app/app.jar"]