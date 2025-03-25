
FROM eclipse-temurin:17-jdk-focal AS build

WORKDIR /app


COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw dependency:go-offline


COPY src ./src
RUN ./mvnw package -DskipTests

FROM eclipse-temurin:17-jre-focal

WORKDIR /app


COPY --from=build /app/target/*.jar app.jar


ENV JAVA_OPTS="-Xms128m -Xmx512m"


CMD ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
