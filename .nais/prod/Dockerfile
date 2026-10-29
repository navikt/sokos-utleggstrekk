FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-25@sha256:8d69742b72bbc0dc8ee87ddac7caa82b985c44407a01b11af54bf9863d419657

COPY build/install/*/lib /lib
COPY .nais/prod/application.conf /app/
ENV TZ="Europe/Oslo"

ENTRYPOINT ["java", "-cp", "/lib/*", "no.nav.sokos.utleggstrekk.ApplicationKt"]