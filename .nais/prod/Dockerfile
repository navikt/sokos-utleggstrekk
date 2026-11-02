FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-25@sha256:f256d7b3ce54e805d87689635a79f1874e46c9e5fb53b97169a3ffcc0659a6de

COPY build/install/*/lib /lib

ENV TZ="Europe/Oslo"
# --enable-native-access=ALL-UNNAMED fjernes når ingen avhengigheter kaster WARNING etterhvert som Java 25 kommer med flere oppdateringer
ENV JDK_JAVA_OPTIONS="-XX:MaxRAMPercentage=75 --enable-native-access=ALL-UNNAMED"

ENTRYPOINT ["java", "-cp", "/lib/*", "no.nav.sokos.utleggstrekk.ApplicationKt"]