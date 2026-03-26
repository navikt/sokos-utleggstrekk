#!/bin/bash

# Ensure user is authenicated, and run login if not.
log "Checking gcloud authentication..."
if ! gcloud auth print-identity-token &>/dev/null; then
    nais auth login
    nais auth login --nais
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
ENV_VARS=$(cat <<'EOF'
MASKINPORTEN_CLIENT_ID
MASKINPORTEN_WELL_KNOWN_URL
MASKINPORTEN_SCOPES
MASKINPORTEN_CLIENT_JWK
MASKINPORTEN_SYSTEMBRUKER_CLAIM
MQ_USERNAME
MQ_PASSWORD
POSTGRES_HOST
POSTGRES_PORT
POSTGRES_USERNAME
POSTGRES_PASSWORD
POSTGRES_JDBC_URL
SOKOS_UTLEGGSTREKK_SLACK_WEBHOOK_URL
UNLEASH_SERVER_API_URL
UNLEASH_SERVER_API_TOKEN
UNLEASH_SERVER_API_ENV
NAIS_APP_NAME
NAIS_POD_NAME
NAIS_CLUSTER_NAME
EOF
)

envValue=$(kubectl exec "$POD_NAME" -c sokos-utleggstrekk -- env | awk -F= 'NR==FNR{allow[$1]=1; next} allow[$1]' <(printf '%s\n' "$ENV_VARS") - | sort)

# Set local environment variables
rm -f defaults.properties
echo "$envValue" > defaults.properties

echo "# proxy jdbc_url " >> defaults.properties
echo "POSTGRES_JDBC_URL=jdbc:postgresql://localhost:5432/sokos-utleggstrekk?user=$(gcloud auth list --filter=status:ACTIVE --format='value(account)')" >> defaults.properties

echo "Environment variables saved to defaults.properties"