FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-25-dev@sha256:65870334a3252c6d56d102d2fed4398328e4aded6a4701909eb4a892fda56637

COPY build/install/*/lib /lib
COPY .nais/dev/application.conf /app/
ENV TZ="Europe/Oslo"

ENTRYPOINT ["java", "-cp", "/lib/*", "no.nav.sokos.utleggstrekk.ApplicationKt"]