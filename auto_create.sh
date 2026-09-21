#!/bin/bash

# Configurazione Risorse Always Free A1 Flex
OCPUs=4
MEMORY_IN_GBS=24
SHAPE="VM.Standard.A1.Flex"
INSTANCE_NAME="lets-table-backend"

# Compartment ID dalla Tenancy autenticata
COMPARTMENT_ID=$OCI_CLI_TENANCY

echo "=== Avvio singolo tentativo per $INSTANCE_NAME ($OCPUs OCPU,${MEMORY_IN_GBS}GB RAM) ==="

# Prepara la chiave SSH usando la chiave pubblica del tuo PC
mkdir -p ~/.ssh
if [ -n "$SSH_PUBLIC_KEY" ]; then
    echo "$SSH_PUBLIC_KEY" > ~/.ssh/id_rsa.pub
else
    echo "Errore: La variabile SSH_PUBLIC_KEY è vuota."
    exit 1
fi

# Trova Availability Domain
AD_NAME=$(oci iam availability-domain list --compartment-id "$COMPARTMENT_ID" --query "data[0].name" --raw-output 2>/dev/null)

if [ -z "$AD_NAME" ]; then
    echo "Errore: Impossibile recuperare l'Availability Domain."
    exit 1
fi

# Cerca l'OCID della subnet pubblica
SUBNET_ID=$(oci network subnet list \
    --compartment-id "$COMPARTMENT_ID" \
    --query "data[?contains(\"display-name\", 'public')].id | [0]" \
    --raw-output 2>/dev/null)

if [ -z "$SUBNET_ID" ] \vert{}\vert{} [ "$SUBNET_ID" == "null" ]; then
    SUBNET_ID=$(oci network subnet list --compartment-id "$COMPARTMENT_ID" --query "data[0].id" --raw-output 2>/dev/null)
fi

# Cerca immagine Ubuntu ARM
IMAGE_ID=$(oci compute image list \
    --compartment-id "$COMPARTMENT_ID" \
    --operating-system "Canonical Ubuntu" \
    --shape "$SHAPE" \
    --query "data[?contains(\"operating-system-version\", '22.04') || contains(\"operating-system-version\", '24.04')].id | [0]" \
    --raw-output 2>/dev/null)

# Lancio dell'istanza
OUTPUT=$(oci compute instance launch \
    --compartment-id "$COMPARTMENT_ID" \
    --availability-domain "$AD_NAME" \
    --shape "$SHAPE" \
    --shape-config "{\"ocpus\": $OCPUs, \"memory_in_gbs\": $MEMORY_IN_GBS}" \
    --display-name "$INSTANCE_NAME" \
    --image-id "$IMAGE_ID" \
    --subnet-id "$SUBNET_ID" \
    --ssh-authorized-keys-file ~/.ssh/id_rsa.pub \
    --assign-public-ip true \
    2>&1)

# 1. VERIFICA DI SUCCESSO
if echo "$OUTPUT" \vert{} grep -q "\"lifecycle-state\": \"PROVISIONING\"" \vert{}\vert{} echo "$OUTPUT" | grep -q "\"id\": \"ocid1.instance"; then
    echo "===================================================="
    echo "SUCCESS! L'istanza $INSTANCE_NAME è stata creata!"
    echo "===================================================="
    
    # Invio notifica Telegram se i secret sono impostati
    if [ -n "$TELEGRAM_BOT_TOKEN" ] && [ -n "$TELEGRAM_CHAT_ID" ]; then
        TEXT="🎉 *Istanza Oracle ARM Creata con successo!*%0A%0A🖥 *Nome:* \`$INSTANCE_NAME\`%0A🚀 L'istanza è in fase di avviamento. Tra un paio di minuti potrai connetterti dal tuo PC Windows via SSH."
        curl -s -X POST "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/sendMessage" \
            -d "chat_id=${TELEGRAM_CHAT_ID}" \
            -d "text=${TEXT}" \
            -d "parse_mode=Markdown" > /dev/null
    fi
    exit 0
fi

# 2. VERIFICA ERRORE CAPACITÀ
if echo "$OUTPUT" | grep -iq "Out of capacity" || echo "$OUTPUT" \vert{} grep -iq "TooManyRequests" \vert{}\vert{} echo "$OUTPUT" | grep -q "500"; then
    echo "Esito: Risorse esaurite su Oracle Cloud. Riproverà al prossimo ciclo."
    exit 0
fi

# 3. QUALSIASI ALTRO ERRORE
echo "ERRORE INASPETTATO OCI:"
echo "$OUTPUT"
exit 1