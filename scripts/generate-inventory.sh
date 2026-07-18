#!/usr/bin/env bash
# Generates infra/ansible/inventory.ini from Terraform outputs
# Run after: terraform -chdir=infra/terraform apply

set -euo pipefail

TERRAFORM_DIR="$(dirname "$0")/../infra/terraform"
INVENTORY_FILE="$(dirname "$0")/../infra/ansible/inventory.ini"

echo "Reading Terraform outputs..."

JENKINS_IP=$(terraform -chdir="$TERRAFORM_DIR" output -raw jenkins_public_ip)
APP_IP=$(terraform -chdir="$TERRAFORM_DIR" output -raw app_public_ip)

if [[ -z "$JENKINS_IP" || -z "$APP_IP" ]]; then
    echo "ERROR: Could not read IPs from Terraform state. Did you run terraform apply?" >&2
    exit 1
fi

cat > "$INVENTORY_FILE" <<EOF
[jenkins]
jenkins-server ansible_host=${JENKINS_IP} ansible_user=ubuntu

[app]
app-server ansible_host=${APP_IP} ansible_user=ubuntu

[all:vars]
ansible_ssh_private_key_file=~/.ssh/clouddelivery-key.pem
ansible_ssh_common_args='-o StrictHostKeyChecking=no'
EOF

echo "Inventory written to: $INVENTORY_FILE"
echo "  Jenkins: ${JENKINS_IP}"
echo "  App:     ${APP_IP}"
