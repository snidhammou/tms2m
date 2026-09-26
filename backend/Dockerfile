FROM eclipse-temurin:17-jdk AS build
WORKDIR /src
COPY . .
RUN ./gradlew --no-daemon bootJar -x test

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /src/build/libs/tms-server-*.jar /app/tms-server.jar
VOLUME /app/data
EXPOSE 8095
ENTRYPOINT ["java", "-jar", "/app/tms-server.jar"]
