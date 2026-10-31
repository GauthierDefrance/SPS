# Image du worker de transcodage : Java 21 + FFmpeg.
# Construite par docker compose (service "worker"). Plusieurs copies : docker compose up -d --scale worker=3

# --- Étape 1 : compilation du jar (les outils de build ne restent pas dans l'image finale) ---
FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
# Dépendances téléchargées dans une couche à part : réutilisée tant que pom.xml ne change pas
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B package -DskipTests

# --- Étape 2 : image d'exécution ---
FROM eclipse-temurin:21-jre-noble
RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /build/target/worker.jar app.jar
# Pas de droits root pour faire tourner FFmpeg sur des fichiers envoyés par des utilisateurs
USER ubuntu
# MaxRAMPercentage : la JVM s'adapte à la mémoire allouée au conteneur
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
