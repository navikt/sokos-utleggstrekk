FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-25-dev@sha256:a42ca1b7512a165445a1017b328c72d46978bccc19b568c70e253300c936dad4

COPY build/install/*/lib /lib
COPY .nais/dev/application.conf /app/
ENV TZ="Europe/Oslo"

ENTRYPOINT ["java", "-cp", "/lib/*", "no.nav.sokos.utleggstrekk.ApplicationKt"]