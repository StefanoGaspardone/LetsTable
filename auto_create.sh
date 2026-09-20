#!/bin/bash

# Configurazione Risorse Always Free A1 Flex
OCPUs=4
MEMORY_IN_GBS=24
SHAPE="VM.Standard.A1.Flex"

# Compartment ID dalla Tenancy autenticata
COMPARTMENT_ID=$OCI_CLI_TENANCY

echo "=== Avvio singolo tentativo per VM.Standard.A1.Flex ($OCPUs OCPU, ${MEMORY_IN_GBS}GB RAM) ==="

# Genera una chiave SSH se non esiste già nel runner
if [ ! -f ~/.ssh/id_rsa.pub ]; then
    mkdir -p ~/.ssh
    ssh-keygen -t rsa -b 2048 -f ~/.ssh/id_rsa -N "" >/dev/null 2>&1
fi

# Trova Availability Domain
AD_NAME=$(oci iam availability-domain list --compartment-id "$COMPARTMENT_ID" --query "data[0].name" --raw-output 2>/dev/null)

if [ -z "$AD_NAME" ]; then
    echo "Errore: Impossibile recuperare l'Availability Domain. Verifica i Secrets OCI su GitHub."
    exit 1
fi

# Cerca immagine Ubuntu ARM (aarch64)
IMAGE_ID=$(oci compute image list \
    --compartment-id "$COMPARTMENT_ID" \
    --operating-system "Canonical Ubuntu" \
    --shape "$SHAPE" \
    --query "data[?contains(\"operating-system-version\", '22.04') || contains(\"operating-system-version\", '24.04')].id | [0]" \
    --raw-output 2>/dev/null)

if [ -z "$IMAGE_ID" ] || [ "$IMAGE_ID" == "null" ]; then
    echo "Errore: Impossibile trovare un'immagine Ubuntu ARM valida per $SHAPE."
    exit 1
fi

# Singolo lancio dell'istanza con aggiunta della chiave SSH
OUTPUT=$(oci compute instance launch \
    --compartment-id "$COMPARTMENT_ID" \
    --availability-domain "$AD_NAME" \
    --shape "$SHAPE" \
    --shape-config "{\"ocpus\": $OCPUs, \"memory_in_gbs\": $MEMORY_IN_GBS}" \
    --display-name "AlwaysFree-ARM-Instance" \
    --image-id "$IMAGE_ID" \
    --ssh-authorized-keys-file ~/.ssh/id_rsa.pub \
    --assign-public-ip true \
    2>&1)

if echo "$OUTPUT" | grep -q "Out of capacity"; then
    echo "Esito: Risorse ancora esaurite (Out of capacity). Il prossimo tentativo verrà eseguito al prossimo cron."
    exit 0
elif echo "$OUTPUT" | grep -q "\"lifecycle-state\": \"PROVISIONING\"" || echo "$OUTPUT" | grep -q "\"id\": \"ocid1.instance"; then
    echo "===================================================="
    echo "SUCCESS! L'istanza è stata creata ed è in fase di provisioning!"
    echo "===================================================="
    exit 0
else
    echo "Messaggio OCI: $OUTPUT"
    exit 1
fi