FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-25@sha256:e132f3f96eb8907608f3e8da3ae926a30fefc36c9b2a28cc9adf3720c29fdbeb

COPY build/install/*/lib /lib
COPY .nais/prod/application.conf /app/
ENV TZ="Europe/Oslo"

ENTRYPOINT ["java", "-cp", "/lib/*", "no.nav.sokos.utleggstrekk.ApplicationKt"]