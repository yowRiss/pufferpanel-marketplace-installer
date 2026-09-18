#!/usr/bin/env bash
# ==============================================================================
# PufferPanel Mod Marketplace - Rollback / Uninstall Script
# Restores vanilla PufferPanel from the latest backup
# ==============================================================================
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec "${SCRIPT_DIR}/install.sh" --restore
