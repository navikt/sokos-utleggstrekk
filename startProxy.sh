#!/bin/bash

if [ -s "$HOME/.config/gcloud/application_default_credentials.json" ]; then
# Ensure user is authenicated, and run login if not.
   gcloud auth print-identity-token &> /dev/null
   if [ $? -gt 0 ]; then
      gcloud auth login
   fi
else
   gcloud auth login --update-adc
fi

if nc -z localhost 5432 2>/dev/null; then
    echo "proxy already running"
else
    nais postgres prepare sokos-utleggstrekk
    nais postgres grant sokos-utleggstrekk
    nais postgres proxy sokos-utleggstrekk
fi