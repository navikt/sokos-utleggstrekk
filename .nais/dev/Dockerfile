FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-25-dev@sha256:7509be568305c47fdb604f2c1842aba4b08964555cdfed6e97f51425e8e16d2a

COPY build/install/*/lib /lib
COPY .nais/dev/application.conf /app/
ENV TZ="Europe/Oslo"

ENTRYPOINT ["java", "-cp", "/lib/*", "no.nav.sokos.utleggstrekk.ApplicationKt"]