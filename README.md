# PufferPanel Mod Marketplace - One-Click Installer

This installer packages the custom **Modrinth Mod Marketplace** feature for PufferPanel so that any standard ("vanilla") PufferPanel installation can use all these features immediately without compiling Go, building node assets, or configuring anything from scratch.

---

## 🚀 Quick Start

To upgrade your standard / vanilla PufferPanel (running on port **8080**) with the Mod Marketplace:

```bash
cd /root/pufferpanel-marketplace-installer
./install.sh
```

Or run non-interactively:
```bash
./install.sh -y
```

---

## 📦 What It Does Automatically

1. **Safety Backup**: Creates a timestamped backup of your current `/usr/sbin/pufferpanel` binary and `/var/www/pufferpanel` web files under `backups/`.
2. **Installs Precompiled Binary**: Copies the precompiled PufferPanel binary (with Modrinth marketplace backend and embedded assets) to `/usr/sbin/pufferpanel`.
3. **Installs Frontend Web Files**: Copies the built frontend assets with the Mod Marketplace UI to `/var/www/pufferpanel`.
4. **Applies Permissions**: Ensures proper ownership for the `pufferpanel` service user.
5. **Restarts Service**: Safely restarts `pufferpanel.service` and verifies that the panel answers HTTP 200.

---

## 🎮 Included Features

- **Modrinth Mod Marketplace**: Direct integration with the Modrinth API inside PufferPanel.
- **Server Configuration Button & Tab**: Dedicated "Mod Marketplace" button beside Settings for all Minecraft: Java Edition servers.
- **Auto Mod Detection**: Detects all `.jar` files in `mods/` (installed through the marketplace, SFTP, or manual upload) with an Uninstall button.
- **Environment & Side Filters**: Filter by Server-side, Client-side, Server Only, Both, or Client Only.
- **Full Mod Description Modal**: Rich markdown descriptions, gallery images, author info, and external links (GitHub, Wiki, Issues, Modrinth).
- **Minecraft Version Dropdown**: Dynamic dropdown fetched from Modrinth's Game Versions API with automatic release vs snapshot grouping and active server version highlight.
- **Dark-Themed Controls**: Clean dark-mode dropdowns and popups with high contrast text.
- **Vanilla Server Support**: Automatic detection for Vanilla Minecraft servers with a 1-click button to enable Fabric Mod Loader.

---

## 🔄 Rollback / Uninstall

If you ever want to revert back to your original vanilla installation:

```bash
./uninstall.sh
```
*(Or `./install.sh --restore`)*

---

## 🛠 Advanced Options

```bash
./install.sh --help

Options:
  --prod, --vanilla   Upgrade vanilla production PufferPanel on port 8080 (default)
  --dev               Update development instance on port 8081
  --restore           Rollback to previous vanilla backup
  -y, --yes           Non-interactive mode (auto confirm)
  -h, --help          Show help message
```
