# Build the runnable distribution with the JDK, run it on a slim JRE.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY . .
RUN ./gradlew :app:installDist --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /opt/barbu
COPY --from=build /src/app/build/install/app/ ./
EXPOSE 8080
ENTRYPOINT ["./bin/app"]
