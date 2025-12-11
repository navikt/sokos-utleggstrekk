FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-21@sha256:2ab80d349d0f4a476f444ed7e2ca0f5080f74cf5e5dac7ed25dd4ea36a40f612

COPY build/install/*/lib /lib

ENV TZ="Europe/Oslo"

ENTRYPOINT ["java", "-cp", "/lib/*", "no.nav.sokos.utleggstrekk.ApplicationKt"]