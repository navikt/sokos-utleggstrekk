FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-25@sha256:2b2691b3133715ab0512856120b97a28f291ca8c0c2ddc382a17a8d0450ab798

COPY build/install/*/lib /lib

ENV TZ="Europe/Oslo"
# --enable-native-access=ALL-UNNAMED fjernes når ingen avhengigheter kaster WARNING etterhvert som Java 25 kommer med flere oppdateringer
ENV JDK_JAVA_OPTIONS="-XX:MaxRAMPercentage=75 --enable-native-access=ALL-UNNAMED"

ENTRYPOINT ["java", "-cp", "/lib/*", "no.nav.sokos.utleggstrekk.ApplicationKt"]