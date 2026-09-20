# HomingExp (Fabric Server-Side Mod)

[![Platform: Fabric](https://img.shields.io/badge/Platform-Fabric-blue.svg)](https://fabricmc.net/)
[![Minecraft: 1.21+ / 26.3](https://img.shields.io/badge/Minecraft-1.21%2B%20%2F%2026.3-brightgreen.svg)](https://minecraft.net/)
[![Side: Server-Only](https://img.shields.io/badge/Side-Server--Only-orange.svg)]()

**HomingExp** is a lightweight, 100% server-side Fabric mod that homes experience orbs directly to whoever killed a mob—even when shot from 64+ blocks away—and offers direct-drop delivery right to the player's feet.

Players do **not** need to install any client mod. It works natively with 100% vanilla Minecraft clients!

---

## 🌟 Key Features

### 1. Long-Distance Killer Homing (Up to 64+ Blocks)
- **Automatic Killer Locking**: When a mob is killed via melee, bow, crossbow, trident, splash potion, or tamed pet, the mod locks the dropped experience orbs to the killer.
- **Sniping From Afar**: Eliminates the frustration of sniping a mob from 64 blocks away and having the XP sit on the ground far away or get stolen by nearby players.
- **Anti-Kill-Steal Protection**: Homing orbs spawned from your kills are exclusively bound to you as they streak towards you.

### 2. Direct-Drop Delivery Mode (`/hexp mode direct`)
- Want instant rewards? In `direct` mode, the mob's experience is spawned directly at your feet the moment the mob dies!
- **Zero Travel Delay**: Collect your experience immediately even if the mob fell into lava, into the void, or died across a massive ravine 64 blocks away.

### 3. Smooth Flight Physics & Obstacle Pass-Through
- **Noclip & Gravity Control**: In `homing` mode, experience orbs disable gravity and accelerate smoothly towards the player (up to 2.5 blocks/tick).
- **Stuck Prevention**: If an orb is blocked by mountains, walls, or underground obstacles, it automatically boosts through barriers and teleports if obstructed, ensuring **0% lost XP**.
- **Sparkle Particle Trail**: Green villager sparkle particles (`HAPPY_VILLAGER`) trace the flight path of homing orbs through the air.

### 4. Loose XP Magnet
- Stray experience orbs in the world (from mining coal, lapis, redstone, diamond, nether quartz, breeding animals, fishing, or smelting) within range automatically magnetize and fly towards nearby active players!

---

## 🎮 Commands & Permissions

| Command | Permission | Description |
| :--- | :--- | :--- |
| `/hexp` or `/hexp help` | Everyone | Displays help menu and command list. |
| `/hexp toggle` | Everyone | Toggles homing experience on or off for yourself. |
| `/hexp mode <homing\|direct\|hybrid>` | Everyone | Selects experience delivery mode: `homing` (flies to you), `direct` (drops at feet), or `hybrid` (direct if >24m, flies if close). |
| `/hexp status` | Everyone | Shows your personal delivery mode and current server range settings. |
| `/hexp range <blocks>` | OP (Level 2) | Configures the maximum homing/delivery distance (default: `64.0` blocks, range: 8–256). |
| `/hexp reload` | OP (Level 2) | Reloads `config/homingexp.json` and player preferences from disk. |

*(Alias: `/homingexp`)*

---

## ⚙️ Configuration (`config/homingexp.json`)

```json
{
  "enabled": true,
  "defaultMode": "homing",
  "maxRange": 64.0,
  "homingSpeed": 1.8,
  "showParticles": true,
  "magnetLooseXp": true,
  "looseXpRange": 48.0,
  "hybridDirectDistance": 24.0
}
```

- `defaultMode`: Default mode for new players (`"homing"`, `"direct"`, or `"hybrid"`).
- `maxRange`: Maximum block distance for homing orbs and direct drop to trigger (default `64.0`).
- `homingSpeed`: Velocity multiplier for flying orbs.
- `showParticles`: Displays emerald sparkle trail behind flying orbs.
- `magnetLooseXp`: Automatically pulls nearby loose world XP orbs (from ores/mining/etc.) to players.

---

## 🔨 Building from Source

To compile the mod from source code:

```bash
cd homingexp
./build.sh
```

The script compiles the Java sources and packages `homingexp-1.0.0.jar`.
