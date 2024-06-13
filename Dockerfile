FROM ghcr.io/navikt/baseimages/temurin:21
COPY build/libs/*.jar app.jar
COPY .initscript /init-scripts