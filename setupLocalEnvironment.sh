#!/bin/bash

# Ensure user is authenicated, and run login if not.
gcloud auth print-identity-token &> /dev/null
if [ $? -gt 0 ]; then
    gcloud auth login
fi

kubectl config use-context dev-gcp
kubectl config set-context --current --namespace=okonomi

# Get AZURE system variables
envValue=$(kubectl exec -it $(kubectl get pods | grep sokos-utleggstrekk | cut -f1 -d' ') -c sokos-utleggstrekk -- env | egrep "^AZURE|^MASKINPORTEN|^MQ_USERNAME|^MQ_PASSWORD|^POSTGRES|^SOKOS_UTLEGGSTREKK|^SKE"| sort)

# Set AZURE as local environment variables
rm -f defaults.properties
echo "$envValue" > defaults.properties

echo "# proxy jdbc_url " >> defaults.properties
echo POSTGRES_JDBC_URL=jdbc:postgresql://localhost:5432/sokos-utleggstrekk?user=`gcloud auth list --filter=status:ACTIVE --format="value(account)"` >> defaults.properties

echo "defaults.properties created successfully."
