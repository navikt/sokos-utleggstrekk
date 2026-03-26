#!/bin/bash

 if ! gcloud auth print-identity-token &>/dev/null; then
       echo "User not logged in, running nais auth login..."
       nais auth login
 fi

  kubectl config use-context dev-gcp
if ! gcloud auth application-default print-access-token &>/dev/null; then
    echo "ADC not configured or invalid, running login with --update-adc..."
    gcloud auth login --update-adc
    nais postgres prepare sokos-utleggstrekk
    nais postgres grant sokos-utleggstrekk
fi



if nc -z localhost 5432 2>/dev/null; then
   sudo systemctl stop postgresql
else
    echo "starting proxy"
    nais postgres proxy sokos-utleggstrekk --reason "lokal utvikling" --team okonomi
fi
