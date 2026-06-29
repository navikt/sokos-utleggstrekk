FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-25-dev@sha256:f0aefd74df6bd451926485a433dbeb7e11aac911046b8815a0b7855d0cdb28dc

COPY build/install/*/lib /lib

ENV TZ="Europe/Oslo"
# --enable-native-access=ALL-UNNAMED fjernes når ingen avhengigheter kaster WARNING etterhvert som Java 25 kommer med flere oppdateringer
ENV JDK_JAVA_OPTIONS="-XX:MaxRAMPercentage=75 --enable-native-access=ALL-UNNAMED"

ENTRYPOINT ["java", "-cp", "/lib/*", "no.nav.sokos.utleggstrekk.ApplicationKt"]