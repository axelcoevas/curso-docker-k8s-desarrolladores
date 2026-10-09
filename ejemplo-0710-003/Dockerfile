FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre

WORKDIR /app

RUN groupadd --system spring_group && useradd --system --gid spring_group --home-dir /app spring

COPY --from=build --chown=spring /app/target/curso-0.0.1-SNAPSHOT.jar app.jar

USER spring:spring_group

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]