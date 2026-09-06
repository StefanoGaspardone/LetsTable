# ---------- Stage 1: Build ----------
FROM eclipse-temurin:21-jdk-jammy AS build

WORKDIR /app

# Copia prima solo i file di configurazione Gradle per sfruttare la cache dei layer:
# se build.gradle.kts non cambia, questo layer (download dipendenze) non viene rifatto
COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts settings.gradle.kts ./

RUN chmod +x ./gradlew

# Scarica le dipendenze separatamente (layer cacheable)
RUN ./gradlew dependencies --no-daemon || true

# Ora copia il resto del codice sorgente
COPY src src

# Build effettiva, saltando i test (li fai girare separatamente in CI)
RUN ./gradlew bootJar --no-daemon -x test

# ---------- Stage 2: Runtime ----------
FROM eclipse-temurin:21-jre-jammy AS runtime

WORKDIR /app

# Utente non-root per sicurezza
RUN groupadd -r spring && useradd -r -g spring spring

# Copia solo il jar finale dallo stage di build
COPY --from=build /app/build/libs/*.jar app.jar

RUN chown spring:spring app.jar
USER spring

EXPOSE 8080

# Profilo attivo passato a runtime (default 'prod', sovrascrivibile)
ENV SPRING_PROFILES_ACTIVE=prod
ENV JAVA_OPTS=""

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]