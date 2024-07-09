#!/bin/bash
export VAULT_ADDR=https://vault.adeo.no
# Ensure user is authenicated, and run login if not.
gcloud auth print-identity-token &> /dev/null
if [ $? -gt 0 ]; then
    gcloud auth login
fi
kubectl config use-context dev-fss
kubectl config set-context --current --namespace=okonomi

# Get database username and password secret from Vault
[[ "$(vault token lookup -format=json | jq '.data.display_name' -r; exit ${PIPESTATUS[0]})" =~ "nav.no" ]] &>/dev/null || vault login -method=oidc -no-print

# Get AZURE system variables
envValue=$(kubectl exec -it $(kubectl get pods | grep sokos-utleggstrekk | cut -f1 -d' ') -c sokos-utleggstrekk -- env | egrep "^MASKINPORTEN|AZURE_APP_CLIENT_ID|AZURE_APP_WELL_KNOWN_URL" )


POSTGRES_USER=$(vault kv get -field=data postgresql/preprod-fss/creds/sokos-utleggstrekk-user)
#POSTGRES_ADMIN=$(vault kv get -field=data postgresql/preprod-fss/creds/sokos-ske-krav-admin)

username=$(echo "$POSTGRES_USER" | awk -F 'username:' '{print $2}' | awk '{print $1}' | sed 's/]$//')
password=$(echo "$POSTGRES_USER" | awk -F 'password:' '{print $2}' | awk '{print $1}' | sed 's/]$//')
rm -f defaults.properties
echo "$envValue" > defaults.properties

echo "POSTGRES_USERNAME=$username" >> defaults.properties
echo "POSTGRES_PASSWORD=$password" >> defaults.properties
