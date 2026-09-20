# ModEnforcer (Fabric Server-Side Mod)

[![Platform: Fabric](https://img.shields.io/badge/Platform-Fabric-blue.svg)](https://fabricmc.net/)
[![Minecraft: 1.21+ / 26.3](https://img.shields.io/badge/Minecraft-1.21%2B%20%2F%2026.3-brightgreen.svg)](https://minecraft.net/)
[![Side: Server-Only](https://img.shields.io/badge/Side-Server--Only-orange.svg)]()

**ModEnforcer** is a lightweight, 100% server-side Fabric mod designed for modern Minecraft servers. Players do **not** need to install ModEnforcer on their client.

It provides mandatory client mod enforcement (e.g., Simple Voice Chat), in-game OP configuration GUI, real-time color-coded Tab list numerical ping, `/ping` and `/backup` commands, and console log spam suppression.

---

## 🌟 Key Features

### 1. Mandatory Client-Side Mod Enforcement
Ensure all players have required client mods installed before playing on your server:
- **Play-Phase Handshake Verification**: Queries client-registered Fabric networking channels and mod-specific handshake protocols (e.g. Simple Voice Chat reflection API).
- **Delayed Grace Period**: Configurable verification timeout (default `3s`) allowing slow clients to finish network channel negotiation without false kicks.
- **Login NPE Protection**: Robust null checks prevent server task crashes when network addons are not yet fully registered by Fabric.
- **Customizable Kick Screen**: If a player is missing required mods, they are disconnected with a clean, formatted message showing mod names and direct download links (Modrinth, CurseForge, etc.).

### 2. Real-Time Numerical Tab Ping & TPS (`TabPingManager`)
Shows each player's actual network latency directly in the Tab player list:
- **100% Server-Side**: Uses vanilla `ClientboundPlayerInfoUpdatePacket` display name formatting. No client mod or resource pack needed!
- **Color-Coded Latency**:
  - 🟢 **Green** (`≤ 75ms`): Excellent connection
  - 🟡 **Yellow** (`76ms - 150ms`): Good connection
  - 🟠 **Gold** (`151ms - 250ms`): Moderate latency
  - 🔴 **Red** (`251ms - 400ms`): High latency / lag
  - 🟣 **Dark Red** (`> 400ms`): Critical lag / packet loss
- **Live Updates**: Periodically broadcasts updated latencies (configurable tick interval, default every 20 ticks / 1 second).

### 3. Interactive In-Game OP GUI (`/modenforcer gui`)
Server operators (OPs) can configure everything directly in-game using an intuitive chest GUI:
- **Master Toggle** (Emerald / Redstone Block): Instantly enable or bypass mod enforcement server-wide.
- **Tab Ping Toggle** (Ender Eye / Ender Pearl): Enable or disable numerical Tab ping display on the fly.
- **Config Reload** (Compass): Reload `config/modenforcer.json` without restarting the server.
- **Individual Mod Toggles**: Click individual items to toggle enforcement for specific mods (Simple Voice Chat, Waystones, Jade, etc.).

### 4. In-Game Commands
| Command | Permission | Description |
| :--- | :--- | :--- |
| `/ping` | Everyone | Shows your current latency (ms) and server TPS. |
| `/ping <player>` | Everyone | Shows the target player's latency and connection state. |
| `/backup` | OP / Console | Triggers an immediate server auto-backup with in-game chat feedback. |
| `/modenforcer gui` | OP / Console | Opens the interactive OP management GUI (alias: `/modcheck gui`). |
| `/modenforcer toggle` | OP / Console | Toggles master mod enforcement on or off. |
| `/modenforcer tabping [enable\|disable\|toggle]` | OP / Console | Toggles numerical Tab ping display. |
| `/modenforcer list` | OP / Console | Lists all configured required mods and their status. |
| `/modenforcer enable <id>` | OP / Console | Enables enforcement for a specific mod ID. |
| `/modenforcer disable <id>` | OP / Console | Disables enforcement for a specific mod ID. |
| `/modenforcer reload` | OP / Console | Reloads configuration from disk. |

### 5. Console Log Spam Suppression (`LogSpamFilter`)
Uses a custom Log4j2 filter to suppress known, harmless repetitive warnings from polluting the server console (e.g., `handleDisconnection() called twice`, keepalive timeout spam).

---

## 📦 Installation

1. Download [`modenforcer-1.0.0+26.3.jar`](file:///root/pufferpanel-marketplace-installer/modenforcer/modenforcer-1.0.0+26.3.jar).
2. Place the `.jar` file into your server's `mods/` directory.
3. Ensure **Fabric Loader** (`>= 0.19.0`) and **Fabric API** are installed.
4. Start your server. A default configuration file will be automatically generated at `config/modenforcer.json`.

---

## ⚙️ Configuration (`config/modenforcer.json`)

```json
{
  "enabled": true,
  "checkDelaySeconds": 3,
  "kickTitle": "§c§l[Required Mods Missing]",
  "kickHeader": "§fTo play on this server, you must install the following client mod(s):",
  "kickFooter": "§7Please install the required mod(s) and rejoin the server.",
  "tabPingEnabled": true,
  "tabPingIntervalTicks": 20,
  "fastPingRefresh": true,
  "pingRefreshIntervalMs": 1000,
  "requiredMods": [
    {
      "id": "voicechat",
      "name": "Simple Voice Chat",
      "channel": "voicechat:secret",
      "url": "https://modrinth.com/mod/simple-voice-chat",
      "enabled": true
    },
    {
      "id": "waystones",
      "name": "Waystones",
      "channel": "waystones:sync_waystones",
      "url": "https://modrinth.com/mod/waystones",
      "enabled": false
    },
    {
      "id": "jade",
      "name": "Jade",
      "channel": "jade:request_data",
      "url": "https://modrinth.com/mod/jade",
      "enabled": false
    }
  ],
  "suppressedLogPatterns": [
    "handleDisconnection() called twice",
    "received voice chat packet from unregistered player"
  ]
}
```

### Configuration Options
- `enabled`: Global toggle for client mod enforcement (`true`/`false`).
- `checkDelaySeconds`: Number of seconds to wait after player joins before kicking if required channels are missing.
- `kickTitle` / `kickHeader` / `kickFooter`: Formatted text shown on the disconnection screen (supports `§` formatting codes).
- `tabPingEnabled`: Whether to show numerical latency in the player Tab list.
- `tabPingIntervalTicks`: How often (in server ticks) the Tab display name is refreshed (20 ticks = 1 second).
- `requiredMods`: List of mods to check:
  - `id`: Identifier slug.
  - `name`: Display name shown in kick screens and OP GUI.
  - `channel`: The Fabric networking channel used by the client mod.
  - `url`: Download URL shown on disconnection screen.
  - `enabled`: Whether this specific mod is currently enforced.
- `suppressedLogPatterns`: List of log substrings to silence from the console.

---

## 🛠️ Building from Source

To compile and package the mod:

```bash
cd modenforcer
./build.sh /path/to/minecraft/server
```

The build script will:
1. Scan the server `versions/` and `libraries/` directory for Minecraft and Fabric classes.
2. Compile all Java source files under [`src/main/java/`](file:///root/pufferpanel-marketplace-installer/modenforcer/src/main/java/).
3. Package the class files and resources into `modenforcer-1.0.0+26.3.jar`.

---

## 📂 Project Structure

```
modenforcer/
├── build.sh                                # Build automation script
├── modenforcer-1.0.0+26.3.jar              # Precompiled ready-to-use mod jar
├── modenforcer.json.example                # Example configuration file
├── README.md                               # Mod documentation (this file)
└── src/
    └── main/
        ├── resources/
        │   ├── fabric.mod.json             # Fabric mod metadata
        │   └── modenforcer.mixins.json     # Mixin configuration
        └── java/
            └── com/
                └── modenforcer/
                    ├── ModEnforcerMod.java # Mod entrypoint & join listeners
                    ├── ModEnforcerConfig.java # Configuration manager & defaults
                    ├── ModEnforcerCommand.java # /modenforcer, /ping, /backup commands
                    ├── ModEnforcerMenu.java # In-game 27-slot Chest GUI
                    ├── TabPingManager.java # Real-time Tab ping manager & packet updates
                    ├── LogSpamFilter.java # Log4j2 console spam suppression
                    └── mixin/
                        ├── ServerPlayerTabMixin.java # Mixin for Tab display name
                        └── ServerCommonPacketListenerMixin.java # Network packet hooks
```

---

## 📄 License
MIT License. Free to use, modify, and distribute for any Minecraft server.
