#!/bin/bash

# Configurazione Risorse Always Free A1 Flex
OCPUs=4
MEMORY_IN_GBS=24
SHAPE="VM.Standard.A1.Flex"

# Compartment ID dalla Tenancy autenticata
COMPARTMENT_ID=$OCI_CLI_TENANCY

echo "=== Avvio singolo tentativo per VM.Standard.A1.Flex ($OCPUs OCPU, ${MEMORY_IN_GBS}GB RAM) ==="

# Prepara la cartella SSH e usa la tua chiave dal Secret di GitHub
mkdir -p ~/.ssh
if [ -n "$SSH_PUBLIC_KEY" ]; then
    echo "$SSH_PUBLIC_KEY" > ~/.ssh/id_rsa.pub
else
    echo "Errore: Variabile SSH_PUBLIC_KEY non trovata."
    exit 1
fi

# Trova Availability Domain
AD_NAME=$(oci iam availability-domain list --compartment-id "$COMPARTMENT_ID" --query "data[0].name" --raw-output 2>/dev/null)

if [ -z "$AD_NAME" ]; then
    echo "Errore: Impossibile recuperare l'Availability Domain. Verifica le credenziali OCI."
    exit 1
fi

# Cerca l'OCID della subnet pubblica (public subnet-lets-table-vcn)
SUBNET_ID=$(oci network subnet list \
    --compartment-id "$COMPARTMENT_ID" \
    --query "data[?contains(\"display-name\", 'public')].id | [0]" \
    --raw-output 2>/dev/null)

# Fallback sulla prima subnet se non trova "public"
if [ -z "$SUBNET_ID" ] || [ "$SUBNET_ID" == "null" ]; then
    SUBNET_ID=$(oci network subnet list --compartment-id "$COMPARTMENT_ID" --query "data[0].id" --raw-output 2>/dev/null)
fi

if [ -z "$SUBNET_ID" ] || [ "$SUBNET_ID" == "null" ]; then
    echo "Errore: Nessuna Subnet trovata nel compartimento."
    exit 1
fi

echo "Usando Subnet OCID: $SUBNET_ID"

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

# Singolo lancio dell'istanza
OUTPUT=$(oci compute instance launch \
    --compartment-id "$COMPARTMENT_ID" \
    --availability-domain "$AD_NAME" \
    --shape "$SHAPE" \
    --shape-config "{\"ocpus\": $OCPUs, \"memory_in_gbs\": $MEMORY_IN_GBS}" \
    --display-name "lets-table-backend" \
    --image-id "$IMAGE_ID" \
    --subnet-id "$SUBNET_ID" \
    --ssh-authorized-keys-file ~/.ssh/id_rsa.pub \
    --assign-public-ip true \
    2>&1)

# 1. VERIFICA DI SUCCESSO (Strict Check)
if echo "$OUTPUT" | grep -q "\"lifecycle-state\": \"PROVISIONING\"" || echo "$OUTPUT" | grep -q "\"id\": \"ocid1.instance"; then
    echo "===================================================="
    echo "SUCCESS! L'istanza è stata creata ed è in fase di provisioning!"
    echo "===================================================="

    # Notifica Telegram
    if [ -n "$TELEGRAM_BOT_TOKEN" ] && [ -n "$TELEGRAM_CHAT_ID" ]; then
        TEXT="🎉 *Istanza lets-table-backend creata con successo!*%0A%0AL'istanza si sta avviando. Tra qualche minuto potrai connetterti dal tuo PC via SSH."
        curl -s -X POST "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/sendMessage" \
            -d "chat_id=${TELEGRAM_CHAT_ID}" \
            -d "text=${TEXT}" \
            -d "parse_mode=Markdown" > /dev/null
    fi

    exit 0
fi

# 2. VERIFICA ERRORE CAPACITÀ (Atteso se i server sono pieni)
if echo "$OUTPUT" | grep -iq "Out of capacity" || echo "$OUTPUT" | grep -iq "TooManyRequests" || echo "$OUTPUT" | grep -q "500"; then
    echo "Esito: Risorse esaurite su Oracle Cloud (Out of capacity). Riproverà al prossimo ciclo."
    exit 0
fi

# 3. QUALSIASI ALTRO ERRORE (Configurazione errata, permessi, sintassi)
echo "----------------------------------------------------"
echo "ERRORE INASPETTATO OCI:"
echo "$OUTPUT"
echo "----------------------------------------------------"
exit 1