#!/bin/sh

if test -f '/var/run/secrets/nais.io/srvutleggstrekk/username'; then
    export MQ_USERNAME=$(cat /var/run/secrets/nais.io/srvutleggstrekk/username)
    echo '- exporting MQ_USERNAME'
fi

if test -f '/var/run/secrets/nais.io/srvutleggstrekk/password'; then
    export MQ_PASSWORD=$(cat /var/run/secrets/nais.io/srvutleggstrekk/password)
    echo '- exporting MQ_PASSWORD'
fi

if test -f '/var/run/secrets/nais.io/srvokonomiadmin/username'; then
    export MQ_INQ_USERNAME=$(cat /var/run/secrets/nais.io/srvokonomiadmin/username)
    echo '- exporting MQ_INQ_USERNAME'
fi


if test -f '/var/run/secrets/nais.io/srvokonomiadmin/password'; then
    export MQ_INQ_PASSWORD=$(cat /var/run/secrets/nais.io/srvokonomiadmin/password)
    echo '- exporting MQ_INQ_PASSWORD'
fi