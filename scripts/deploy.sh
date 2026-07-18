#!/usr/bin/env bash
# Full deployment: Terraform apply → generate inventory → Ansible provisioning
# Usage: ./scripts/deploy.sh
# Prerequisites: AWS CLI configured, key pair created, terraform.tfvars set

set -euo pipefail

ROOT_DIR="$(dirname "$0")/.."
TERRAFORM_DIR="$ROOT_DIR/infra/terraform"
ANSIBLE_DIR="$ROOT_DIR/infra/ansible"

echo "========================================"
echo "  CloudDelivery — Full Deploy"
echo "========================================"

# 1. Terraform
echo ""
echo "[1/3] Running terraform apply..."
terraform -chdir="$TERRAFORM_DIR" init -upgrade
terraform -chdir="$TERRAFORM_DIR" apply -auto-approve

echo ""
echo "[2/3] Generating Ansible inventory..."
"$ROOT_DIR/scripts/generate-inventory.sh"

# Wait for EC2 SSH to become available
echo ""
echo "  Waiting 30s for EC2 instances to be reachable..."
sleep 30

# 2. Ansible
echo ""
echo "[3/3] Running Ansible playbook..."
cd "$ANSIBLE_DIR"
ansible-playbook site.yml -v

echo ""
echo "========================================"
echo "  DEPLOYMENT COMPLETE"
echo "========================================"
terraform -chdir="$TERRAFORM_DIR" output
echo ""
echo "  Jenkins: $(terraform -chdir="$TERRAFORM_DIR" output -raw jenkins_url)"
echo "  App:     $(terraform -chdir="$TERRAFORM_DIR" output -raw app_url)"
echo ""
echo "  To destroy: ./scripts/destroy.sh"
