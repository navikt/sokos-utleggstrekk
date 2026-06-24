#!/bin/bash

if [ "$#" -ne 1 ]; then
  if [ "$#" -eq 0 ]; then
    echo 'No argument supplied. Provide a reason for connecting to the database'
  else
    echo "Too many arguments supplied. The reason should be quoted."
  fi
    echo 'Usage: ./startProxy.sh "TOB-XXX: Kontrollere at ..."'
    exit 1
fi

if ! gcloud auth print-identity-token &>/dev/null; then
    echo "User not logged in, running nais auth login..."
    nais auth login
fi

kubectl config use-context dev-gcp
if ! gcloud auth application-default print-access-token &>/dev/null; then
    echo "ADC not configured or invalid, running login with --update-adc..."
    gcloud auth login --update-adc
    nais postgres prepare sokos-utleggstrekk --team okonomi   -environment dev-gcp
    nais postgres grant sokos-utleggstrekk --team okonomi -environment dev-gcp
fi



if nc -z localhost 5432 2>/dev/null; then
    echo "Port 5432 is in use. Attempting to stop local PostgreSQL..."
    if command -v brew &>/dev/null; then
        brew services stop postgresql || true
    elif command -v systemctl &>/dev/null; then
        systemctl stop postgresql || true
    fi
    if nc -z localhost 5432 2>/dev/null; then
        echo "Port 5432 is still in use. Please free it manually and re-run the script."
        exit 1
    fi
fi

echo "Starting NAIS postgres proxy..."
nais postgres proxy sokos-utleggstrekk --reason "$1" --team okonomi --environment dev-gcp
