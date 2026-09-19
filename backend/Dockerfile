# ---------- Stage 1: Build ----------
FROM eclipse-temurin:21-jdk-jammy AS build

WORKDIR /app

ENV GRADLE_OPTS="-Dorg.gradle.jvmargs='-Xmx384m -XX:MaxMetaspaceSize=128m'"

COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts settings.gradle.kts ./

RUN chmod +x ./gradlew
RUN ./gradlew dependencies --no-daemon || true

COPY src src

RUN ./gradlew bootJar --no-daemon -x test

# ---------- Stage 2: Runtime ----------
FROM eclipse-temurin:21-jre-jammy AS runtime

WORKDIR /app

RUN groupadd -r spring && useradd -r -g spring spring

COPY --from=build /app/build/libs/*.jar app.jar

RUN chown spring:spring app.jar
USER spring

EXPOSE 8080

ENV SPRING_PROFILES_ACTIVE=prod
ENV JAVA_OPTS="-Xmx256m -Xss512k -XX:MaxMetaspaceSize=128m"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]