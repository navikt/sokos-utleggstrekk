FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-25@sha256:3d76c31d31b86fa7a3da09fa82ca0dd139343a070f588f8a931c496bcbd074d3

COPY build/install/*/lib /lib

ENV TZ="Europe/Oslo"
# --enable-native-access=ALL-UNNAMED fjernes når ingen avhengigheter kaster WARNING etterhvert som Java 25 kommer med flere oppdateringer
ENV JDK_JAVA_OPTIONS="-XX:MaxRAMPercentage=75 --enable-native-access=ALL-UNNAMED"

ENTRYPOINT ["java", "-cp", "/lib/*", "no.nav.sokos.utleggstrekk.ApplicationKt"]