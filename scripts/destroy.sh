#!/usr/bin/env bash
# Destroys all AWS infrastructure — run when done to avoid charges
# Usage: ./scripts/destroy.sh

set -euo pipefail

ROOT_DIR="$(dirname "$0")/.."
TERRAFORM_DIR="$ROOT_DIR/infra/terraform"

echo "========================================"
echo "  CloudDelivery — DESTROY INFRASTRUCTURE"
echo "========================================"
echo ""
echo "WARNING: This will delete all AWS resources in this workspace."
read -r -p "Type 'yes' to confirm: " CONFIRM

if [[ "$CONFIRM" != "yes" ]]; then
    echo "Aborted."
    exit 0
fi

terraform -chdir="$TERRAFORM_DIR" destroy -auto-approve

echo ""
echo "All AWS resources destroyed. No further charges will accrue."
