# AutoClicker (Fabric Server-Side Mod)

[![Platform: Fabric](https://img.shields.io/badge/Platform-Fabric-blue.svg)](https://fabricmc.net/)
[![Minecraft: 1.21+ / 26.3](https://img.shields.io/badge/Minecraft-1.21%2B%20%2F%2026.3-brightgreen.svg)](https://minecraft.net/)
[![Side: Server-Only](https://img.shields.io/badge/Side-Server--Only-orange.svg)]()

**AutoClicker** is a robust, 100% server-side Fabric mod designed for survival, skyblock, and grinding servers. It allows players to safely automate attack swings and block mining without external client-side macro software or banned cheat clients.

Players do **not** need to install any client mod. It works with 100% vanilla Minecraft clients.

---

## 🌟 Key Features

### 1. Dual Mode Auto-Clicking
- **Attack Mode (`/ac attack [cps]`)**:
  - Automatically swings mainhand weapon at targeted entities.
  - **Full Weapon Charge**: Synchronizes with vanilla 1.9+ weapon cooldown (attack delay) to guarantee 100% maximum damage and proper Sweeping Edge attacks.
  - Configurable Clicks Per Second (CPS).
- **Mine Mode (`/ac mine [cps]`)**:
  - Continuously breaks the block currently in crosshairs.
  - **Authentic Crack Stages**: Transmits vanilla block break progress animation packets (crack stages 0–9) for full visual fidelity.
  - **Auto-Retargeting**: Seamlessly retargets new blocks that appear in crosshairs—ideal for AFK cobblestone generators, obsidian generators, and tree farms.

### 2. Intelligent Tool Durability Protection
- Detects the remaining durability of the held tool during active mining or combat.
- Automatically halts clicking when tool durability reaches $\le 2$ (or configured threshold), ensuring high-value Mending or god-tier tools are never broken accidentally.

### 3. First-Join On-Screen Title/Subtitle Display
- When a new player connects to the server for the first time, a stylish on-screen Title & Subtitle notification appears:
  - Title: `§6§lAutoClicker`
  - Subtitle: `§eUse §b/ac help §eto view available autoclicker modes!`
- **Zero Global Chat Pollution**: Sent purely as a personal client HUD packet (`ClientboundSetTitleTextPacket` / `ClientboundSetSubtitleTextPacket`), avoiding noisy chat broadcasts.
- Remembers acknowledged players in `config/autoclicker/welcomed_players.json` across server restarts.

### 4. PvP Safety Toggle
- Safe by default: Players can toggle whether combat autoclicker targets other players or only hostile/passive mobs (`/ac pvp on|off`).

---

## 🎮 Commands

| Command | Permission | Description |
| :--- | :--- | :--- |
| `/ac help` | Everyone | Displays help menu with syntax and instructions. |
| `/ac attack [cps]` | Everyone | Starts or stops attack autoclicker (default: 4 CPS / weapon cooldown). |
| `/ac mine [cps]` | Everyone | Starts or stops block mining autoclicker (default: 4 CPS). |
| `/ac stop` | Everyone | Stops all running autoclicker sessions immediately. |
| `/ac status` | Everyone | Displays active mode, current CPS, and tool durability status. |
| `/ac toolprotection [on\|off]` | Everyone | Toggles auto-stop when tool reaches critical low durability ($\le 2$). |
| `/ac pvp [on\|off]` | Everyone | Toggles whether attack mode is permitted against players. |

---

## 🔨 Building from Source

To compile the mod from source code:

```bash
cd autoclicker
./build.sh
```

The script compiles the Java sources and packages `autoclicker-1.0.2.jar`.
