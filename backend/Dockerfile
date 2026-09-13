FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /workspace

COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw --batch-mode dependency:go-offline

COPY src src
RUN ./mvnw --batch-mode package -DskipTests \
    && cp target/*.jar /tmp/application.jar

FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S spring \
    && adduser -S -G spring spring

WORKDIR /application
COPY --from=build --chown=spring:spring /tmp/application.jar application.jar

USER spring:spring
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "application.jar"]
