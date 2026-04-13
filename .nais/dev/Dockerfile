FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-25-dev@sha256:32d6876542b1d4148bc887b3cf5710bac369b83617c30eec84654fbee3931496

COPY build/install/*/lib /lib

ENV TZ="Europe/Oslo"
# --enable-native-access=ALL-UNNAMED fjernes når ingen avhengigheter kaster WARNING etterhvert som Java 25 kommer med flere oppdateringer
ENV JDK_JAVA_OPTIONS="-XX:MaxRAMPercentage=75 --enable-native-access=ALL-UNNAMED"

ENTRYPOINT ["java", "-cp", "/lib/*", "no.nav.sokos.utleggstrekk.ApplicationKt"]