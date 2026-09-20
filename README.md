# PufferPanel Mod Marketplace & Server Enhancements - One-Click Installer

This installer packages the custom **Modrinth Mod Marketplace** and advanced server enhancement suite for PufferPanel so that any standard ("vanilla") PufferPanel installation can use all these features immediately without compiling Go, building node assets, or configuring anything from scratch.

---

## 🚀 Quick Start

To upgrade your standard / vanilla PufferPanel (running on port **8080**) with the Mod Marketplace and all enhancements:

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
2. **Installs Precompiled Binary**: Copies the latest precompiled PufferPanel binary (with Modrinth marketplace backend, unquoted JVM arguments passthrough, and embedded assets) to `/usr/sbin/pufferpanel`.
3. **Installs Frontend Web Files**: Copies the built frontend assets with the Mod Marketplace UI, Backup management tab, and Network Traffic Analytics to `/var/www/pufferpanel`.
4. **Installs Auto-Backup System**: Deploys `pufferpanel-auto-backup` CLI, systemd timer (running every 6 hours), and automated 3-file rotation policy.
5. **Applies Permissions**: Ensures proper ownership for the `pufferpanel` service user and verifies data directories.
6. **Restarts Service**: Safely restarts `pufferpanel.service` and verifies that the panel answers HTTP 200.

---

## 🎮 Included Features

### 🛒 Modrinth Mod Marketplace
- **Direct Integration**: Browse and search tens of thousands of mods directly inside PufferPanel.
- **Auto Mod Detection**: Detects all `.jar` files in `mods/` (installed through the marketplace, SFTP, or manual upload) with an Uninstall button.
- **Environment & Side Filters**: Filter by Server-side, Client-side, Server Only, Both, or Client Only.
- **Full Mod Description Modal**: Rich markdown descriptions, gallery images, author info, and external links (GitHub, Wiki, Issues, Modrinth).
- **Minecraft Version Dropdown**: Dynamic dropdown fetched from Modrinth's Game Versions API with automatic release vs snapshot grouping and active server version highlight.
- **1-Click Mod Updates**: Detects outdated mods and updates them directly with a single click.

### 💾 Automated Backups with 3-File Rotation
- **Zero World Corruption**: Safely executes `save-off` and `save-all flush` before archiving and resumes with `save-on`.
- **Strict 3-File Retention**: Automatically deletes the oldest backup from disk and the database whenever a 4th backup is created.
- **PufferPanel Web UI Integration**: Fully visible and downloadable directly from the server's **Backup** tab.
- **Discord Webhook Alerts**: Sends rich embeds with file size, duration, and rotated file info.
- **In-Game / Console Command**: Trigger an instant backup anytime via `/backup` or console `pufferpanel-auto-backup`.

### ⚡ JVM & Network Enhancements
- **JVM Arguments Passthrough**: Allows passing complex `-javaagent:` and `-D` properties without unwanted shell-quoting.
- **500-Character System Chat**: Pre-bundled `ChatLengthAgent.jar` extending system chat messages (e.g. `/whisper`) from 256 to 500 characters.
- **[ModEnforcer Server Mod](modenforcer/README.md)**: Dedicated server-side Fabric mod enforcing mandatory client mods (e.g. Simple Voice Chat), in-game OP GUI (`/modenforcer gui`), numerical real-time Tab ping (`[45ms]`), `/ping` & `/backup` commands, and log spam filter. See the [ModEnforcer Guide](modenforcer/README.md) for full documentation and source code.
- **[CustomJukebox Server Mod](customjukebox/README.md)**: 100% server-side custom music disc system with 3D locational audio via Simple Voice Chat, seamless song auto-looping, silent disc ejects, and in-game throttled background MP3 downloader (`/musicdisc add <url>`) with whisper ETA. See the [CustomJukebox Guide](customjukebox/README.md).
- **[AutoClicker Server Mod](autoclicker/README.md)**: 100% server-side automated attack and mining suite (`/ac attack`, `/ac mine`). Supports full 1.9+ weapon cooldown sync, authentic block crack stages (0-9), intelligent low-durability tool protection, and first-join on-screen title notifications without chat spam. See the [AutoClicker Guide](autoclicker/README.md).
- **[HomingExp Server Mod](homingexp/README.md)**: 100% server-side experience orb enhancement that homes XP directly to whoever killed a mob from up to 64+ blocks away, offers direct-drop delivery at player feet (`/hexp mode direct`), noclip obstacle traversal with particle trails, and loose XP magnetization. See the [HomingExp Guide](homingexp/README.md).
- **Live Status & Discord Monitor ([Addon](addons/status-monitor/README.md))**: Real-time status cards in PufferPanel's Stats tab showing **Server Ping**, 24h/7d uptime rate, downtime history (e.g. `Down 15h ago`), and in-game players, combined with a single live-updating Discord webhook message (zero channel spam).
- **Real-Time Discord Logger**: Monitors server logs for crashes, triggers staff mentions (`@mention`), provides root cause diagnoses, and alerts on abnormal network usage spikes.

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
