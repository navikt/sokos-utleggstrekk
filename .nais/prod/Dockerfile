FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-25@sha256:81710d77feef360cffb80ade6ddab055555b4fd134955dff20da34b8412848da

COPY build/install/*/lib /lib
COPY .nais/prod/application.conf /app/
ENV TZ="Europe/Oslo"

ENTRYPOINT ["java", "-cp", "/lib/*", "no.nav.sokos.utleggstrekk.ApplicationKt"]