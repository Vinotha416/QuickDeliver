# Builds and runs the Java backend (used by Render / Railway / Fly.io / any Docker host).
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY backend/pom.xml backend/pom.xml
COPY backend/src backend/src
COPY frontend/public frontend/public
RUN mvn -q -f backend/pom.xml -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/backend/target/quickdeliver-*.jar app.jar
# keep memory low enough for 512 MB free tiers
ENV JAVA_TOOL_OPTIONS="-Xmx384m"
EXPOSE 5000
ENTRYPOINT ["java", "-jar", "app.jar"]
