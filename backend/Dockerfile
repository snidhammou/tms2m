FROM eclipse-temurin:17-jdk AS build
WORKDIR /src
COPY . .
# chmod / sed : sources parfois empaquetées depuis Windows (bit d'exécution et fins de ligne perdus)
RUN chmod +x gradlew && sed -i 's/\r$//' gradlew && ./gradlew --no-daemon bootJar -x test

FROM eclipse-temurin:17-jre
WORKDIR /app
# Utilisateur non root
RUN useradd --system --uid 1001 tms && mkdir -p /app/data && chown -R tms /app
COPY --from=build /src/build/libs/tms-server-*.jar /app/tms-server.jar
USER tms
VOLUME /app/data
EXPOSE 8095
ENTRYPOINT ["java", "-jar", "/app/tms-server.jar"]
