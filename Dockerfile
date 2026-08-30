FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B -DskipTests dependency:go-offline
COPY src src
RUN ./mvnw -B -DskipTests package

FROM eclipse-temurin:21-jre-jammy
RUN apt-get update \
    && apt-get install --yes --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 wedding \
    && useradd --system --uid 10001 --gid wedding wedding
WORKDIR /app
COPY --from=build /workspace/target/*.jar app.jar
USER wedding
EXPOSE 8080 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
