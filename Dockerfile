FROM maven:3-eclipse-temurin-21 AS build
ARG MODULE
WORKDIR /app

COPY . .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -pl ${MODULE} -am package -DskipTests

FROM eclipse-temurin:21-jre
ARG MODULE
WORKDIR /app

RUN useradd --system --shell /usr/sbin/nologin orderflow
USER orderflow

COPY --from=build /app/${MODULE}/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
