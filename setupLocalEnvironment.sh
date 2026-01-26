#!/bin/bash

# Ensure user is authenicated, and run login if not.
gcloud auth print-identity-token &> /dev/null
if [ $? -gt 0 ]; then
    gcloud auth login
fi

# Suppress kubectl config output
kubectl config use-context dev-gcp
kubectl config set-context --current --namespace=okonomi

# Get pod name
POD_NAME=$(kubectl get pods --no-headers | grep sokos-utleggstrekk | head -n1 | awk '{print $1}')

if [ -z "$POD_NAME" ]; then
    echo "Error: No sokos-utleggstrekk pod found" >&2
    exit 1
fi

echo "Fetching environment variables from pod: $POD_NAME"

# Get system variables
envValue=$(kubectl exec "$POD_NAME" -c sokos-utleggstrekk -- env | egrep "^AZURE|^MASKINPORTEN|^MQ_USERNAME|^MQ_PASSWORD|^POSTGRES|^SOKOS_UTLEGGSTREKK|^SKE"| sort)

# Set local environment variables
rm -f defaults.properties
echo "$envValue" > defaults.properties

echo "# proxy jdbc_url " >> defaults.properties
echo POSTGRES_JDBC_URL=jdbc:postgresql://localhost:5432/sokos-utleggstrekk?user=`gcloud auth list --filter=status:ACTIVE --format="value(account)"` >> defaults.properties

echo "Environment variables saved to defaults.properties"