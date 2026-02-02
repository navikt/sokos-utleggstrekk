#!/bin/bash

# shellcheck disable=SC2034
ADC_FILE="$HOME/.config/gcloud/application_default_credentials.json"
NAIS_CLIENT_ID=dev-gcp:okonomi:sokos-utleggstrekk

if ! gcloud auth application-default print-access-token >/dev/null 2>&1; then
    echo "ADC not configured or invalid, running login with --update-adc..."
    gcloud auth login --update-adc
    nais postgres prepare sokos-utleggstrekk
    nais postgres grant sokos-utleggstrekk
fi

if ! gcloud auth print-identity-token >/dev/null 2>&1; then
    echo "User not logged in, running gcloud auth login..."
    gcloud auth login
fi

if nc -z localhost 5432 2>/dev/null; then
    echo "Port 5432 is busy!"
else
    nais postgres proxy sokos-utleggstrekk
fi
