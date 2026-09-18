#!/usr/bin/env bash
# ==============================================================================
# PufferPanel Mod Marketplace - One-Click Installer
# Installs custom Modrinth Mod Marketplace feature onto vanilla/stock PufferPanel
# ==============================================================================
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BIN_SOURCE="${SCRIPT_DIR}/bin/pufferpanel"
WEB_SOURCE="${SCRIPT_DIR}/web"
BACKUP_BASE="${SCRIPT_DIR}/backups"

PROD_BIN="/usr/sbin/pufferpanel"
PROD_WEB="/var/www/pufferpanel"
PROD_SERVICE="pufferpanel.service"
PROD_PORT=8080

DEV_BIN="/root/development/pufferpanel/pufferpanel"
DEV_WEB="/var/www/pufferpanel"
DEV_SERVICE="pufferpanel-dev.service"
DEV_PORT=8081

# Colors
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m'

print_banner() {
    echo -e "${CYAN}${BOLD}"
    echo "================================================================"
    echo "       PufferPanel Mod Marketplace - Simple Installer           "
    echo "     Direct Modrinth integration for Minecraft Java Servers     "
    echo "================================================================"
    echo -e "${NC}"
}

check_root() {
    if [[ $EUID -ne 0 ]]; then
        echo -e "${RED}[ERROR] This installer must be run as root (use sudo ./install.sh)${NC}"
        exit 1
    fi
}

check_sources() {
    if [[ ! -f "${BIN_SOURCE}" && -f "${BIN_SOURCE}.gz" ]]; then
        echo -e "${BLUE}==>${NC} Decompressing precompiled PufferPanel binary..."
        gunzip -k "${BIN_SOURCE}.gz"
        chmod +x "${BIN_SOURCE}"
    fi

    if [[ ! -f "${BIN_SOURCE}" ]]; then
        echo -e "${RED}[ERROR] Binary source not found: ${BIN_SOURCE}${NC}"
        exit 1
    fi
    if [[ ! -d "${WEB_SOURCE}" ]]; then
        echo -e "${RED}[ERROR] Web assets source not found: ${WEB_SOURCE}${NC}"
        exit 1
    fi
}

backup_vanilla() {
    local target_bin="$1"
    local target_web="$2"
    local timestamp
    timestamp="$(date +'%Y%m%d-%H%M%S')"
    local backup_dir="${BACKUP_BASE}/vanilla-${timestamp}"

    echo -e "${BLUE}==>${NC} Creating backup of current vanilla installation..."
    mkdir -p "${backup_dir}"

    if [[ -f "${target_bin}" ]]; then
        cp "${target_bin}" "${backup_dir}/pufferpanel.orig"
        echo -e "    Saved binary: ${backup_dir}/pufferpanel.orig"
    fi

    if [[ -d "${target_web}" ]]; then
        cp -r "${target_web}" "${backup_dir}/www"
        echo -e "    Saved web files: ${backup_dir}/www"
    fi

    echo "${backup_dir}" > "${BACKUP_BASE}/latest"
    echo -e "${GREEN}[OK] Backup created successfully in ${backup_dir}${NC}"
}

do_install() {
    local target_bin="$1"
    local target_web="$2"
    local service_name="$3"
    local port="$4"
    local label="$5"

    echo ""
    echo -e "${BOLD}Target: ${label}${NC}"
    echo "----------------------------------------------------"
    echo "Binary destination:  ${target_bin}"
    echo "Web files:           ${target_web}"
    echo "Service to restart:  ${service_name}"
    echo "Port:                ${port}"
    echo "----------------------------------------------------"

    backup_vanilla "${target_bin}" "${target_web}"

    echo -e "${BLUE}==>${NC} Stopping ${service_name}..."
    if systemctl is-active --quiet "${service_name}"; then
        systemctl stop "${service_name}"
    fi

    echo -e "${BLUE}==>${NC} Installing updated PufferPanel binary..."
    mkdir -p "$(dirname "${target_bin}")"
    cp "${BIN_SOURCE}" "${target_bin}"
    chmod +x "${target_bin}"

    echo -e "${BLUE}==>${NC} Installing updated web frontend assets..."
    mkdir -p "${target_web}"
    cp -r "${WEB_SOURCE}/"* "${target_web}/"

    # Set permissions if pufferpanel user exists
    if id "pufferpanel" &>/dev/null; then
        chown -R pufferpanel:pufferpanel "${target_web}" 2>/dev/null || true
        chown pufferpanel:pufferpanel "${target_bin}" 2>/dev/null || true
    fi

    echo -e "${BLUE}==>${NC} Starting ${service_name}..."
    systemctl start "${service_name}"

    echo -e "${BLUE}==>${NC} Verifying service health on port ${port}..."
    sleep 3
    local retries=10
    local success=0
    for ((i=1; i<=retries; i++)); do
        local code
        code=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:${port}/" 2>/dev/null || echo "000")
        if [[ "$code" == "200" ]]; then
            success=1
            break
        fi
        sleep 1
    done

    if [[ $success -eq 1 ]]; then
        echo -e "${GREEN}${BOLD}[SUCCESS] ${label} has been upgraded with the Mod Marketplace!${NC}"
        echo ""
        echo -e "Access your panel at: ${CYAN}http://<your-server-ip>:${port}${NC}"
        echo -e "Remember to hard-refresh browser (${YELLOW}Ctrl + F5${NC} or ${YELLOW}Cmd + Shift + R${NC}) to load the new interface."
        echo -e "To restore previous vanilla version at any time, run: ${YELLOW}./uninstall.sh${NC}"
    else
        echo -e "${YELLOW}[WARNING] Service started but port ${port} did not immediately respond with 200.${NC}"
        echo "Check status with: systemctl status ${service_name}"
    fi
}

do_restore() {
    if [[ ! -f "${BACKUP_BASE}/latest" ]]; then
        echo -e "${RED}[ERROR] No backup record found in ${BACKUP_BASE}${NC}"
        exit 1
    fi

    local backup_dir
    backup_dir="$(cat "${BACKUP_BASE}/latest")"

    if [[ ! -d "${backup_dir}" ]]; then
        echo -e "${RED}[ERROR] Backup directory does not exist: ${backup_dir}${NC}"
        exit 1
    fi

    echo -e "${BLUE}==>${NC} Restoring from ${backup_dir}..."

    if systemctl is-active --quiet "${PROD_SERVICE}"; then
        echo "Stopping ${PROD_SERVICE}..."
        systemctl stop "${PROD_SERVICE}"
    fi

    if [[ -f "${backup_dir}/pufferpanel.orig" ]]; then
        cp "${backup_dir}/pufferpanel.orig" "${PROD_BIN}"
        chmod +x "${PROD_BIN}"
        echo "Restored ${PROD_BIN}"
    fi

    if [[ -d "${backup_dir}/www" ]]; then
        rm -rf "${PROD_WEB}"/*
        cp -r "${backup_dir}/www/"* "${PROD_WEB}/"
        echo "Restored ${PROD_WEB}"
    fi

    if id "pufferpanel" &>/dev/null; then
        chown -R pufferpanel:pufferpanel "${PROD_WEB}" 2>/dev/null || true
        chown pufferpanel:pufferpanel "${PROD_BIN}" 2>/dev/null || true
    fi

    echo "Restarting ${PROD_SERVICE}..."
    systemctl start "${PROD_SERVICE}"
    echo -e "${GREEN}[OK] Successfully restored original vanilla PufferPanel.${NC}"
}

# Main routing
check_root
check_sources
print_banner

MODE=""
AUTO_CONFIRM=0

while [[ $# -gt 0 ]]; do
    case "$1" in
        --prod|--vanilla)
            MODE="prod"
            shift
            ;;
        --dev)
            MODE="dev"
            shift
            ;;
        --restore|--rollback)
            MODE="restore"
            shift
            ;;
        -y|--yes)
            AUTO_CONFIRM=1
            shift
            ;;
        -h|--help)
            echo "Usage: $0 [OPTIONS]"
            echo ""
            echo "Options:"
            echo "  --prod, --vanilla   Upgrade vanilla production PufferPanel on port 8080 (default)"
            echo "  --dev               Update development instance on port 8081"
            echo "  --restore           Rollback to previous vanilla backup"
            echo "  -y, --yes           Non-interactive mode (auto confirm)"
            echo "  -h, --help          Show this help message"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            exit 1
            ;;
    esac
done

if [[ -z "$MODE" ]]; then
    if [[ $AUTO_CONFIRM -eq 1 ]]; then
        MODE="prod"
    else
        echo "Please select what you would like to do:"
        echo -e "  ${BOLD}1)${NC} Upgrade Vanilla PufferPanel (Production on Port 8080) [Recommended]"
        echo -e "  ${BOLD}2)${NC} Update Development PufferPanel (Port 8081)"
        echo -e "  ${BOLD}3)${NC} Restore Previous Vanilla Installation (Rollback)"
        echo -e "  ${BOLD}4)${NC} Exit"
        echo ""
        read -rp "Enter choice [1-4] (default 1): " choice
        case "$choice" in
            2) MODE="dev" ;;
            3) MODE="restore" ;;
            4) echo "Exiting."; exit 0 ;;
            *) MODE="prod" ;;
        esac
    fi
fi

case "$MODE" in
    prod)
        if [[ $AUTO_CONFIRM -ne 1 ]]; then
            echo ""
            read -rp "This will upgrade your vanilla PufferPanel (/usr/sbin/pufferpanel) with the Mod Marketplace. Proceed? [Y/n] " confirm
            if [[ "$confirm" =~ ^[Nn] ]]; then
                echo "Aborted."
                exit 0
            fi
        fi
        do_install "${PROD_BIN}" "${PROD_WEB}" "${PROD_SERVICE}" "${PROD_PORT}" "Vanilla Production PufferPanel (Port 8080)"
        ;;
    dev)
        do_install "${DEV_BIN}" "${DEV_WEB}" "${DEV_SERVICE}" "${DEV_PORT}" "Development PufferPanel (Port 8081)"
        ;;
    restore)
        do_restore
        ;;
esac
