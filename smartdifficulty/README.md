# SmartDifficulty (Fabric Server-Side Mod)

[![Platform: Fabric](https://img.shields.io/badge/Platform-Fabric-blue.svg)](https://fabricmc.net/)
[![Minecraft: 1.21+ / 26.3](https://img.shields.io/badge/Minecraft-1.21%2B%20%2F%2026.3-brightgreen.svg)](https://minecraft.net/)
[![Side: Server-Only](https://img.shields.io/badge/Side-Server--Only-orange.svg)]()

**SmartDifficulty** is a 100% server-side Fabric mod that adds an in-game **World Day Counter** and **Smart Scaling Difficulty**. As in-game days advance, monsters progressively adapt with better armor, weapons, and enhanced attributes. Special astronomical events—such as the **Full Moon**—prohibit sleeping in beds and send hordes of empowered hostiles into the night.

Players do **not** need to install any client mod. It works natively with 100% vanilla Minecraft clients!

---

## 🌟 Key Features

### 1. In-Game World Day Counter (`/day`)
- Real-time calculation of elapsed world days and clock time.
- Displays current **Day Number**, **Time of Day** (HH:MM), **Moon Phase**, and active **Difficulty Tier**.
- At the break of dawn every day, a notification chimes and displays the new day's arrival.

### 2. Progressive Mob Scaling (Day Tiers)
Hostile monsters naturally equip better gear and receive stat buffs as world days progress:

- **Tier 1 (Days 1–24)**: Early game survival. Rare leather/gold armor piece (15%).
- **Tier 2 (Days 25–49)**: Developing world. Leather, gold, chainmail, and copper armor pieces (35%). Stone & copper weapons. +10% Health.
- **Tier 3 (Days 50–99)**: Hardened hostiles. Copper, chainmail, and iron armor (60%). Copper & iron weapons. +25% Health, +10% Speed.
- **Tier 4 (Days 100–199) - Iron Era**:
  - Standard zombies spawn wearing **Iron Armor** (85% chance).
  - High probability of full iron armor or partial iron armor mixed with copper/chainmail.
  - Weapons: Iron Swords, Iron Axes, Copper Swords.
  - +40% Health, +15% Speed, +25% Attack Damage.
- **Tier 5 (Days 200–299) - Diamond Era & Randomized Equipment**:
  - **Diamond Armor Pool Unlocked!** (95% chance of armor).
  - **Independent Slot Randomizer**: Each armor slot (Head, Chest, Legs, Feet) rolls independently!
    - Can spawn with **Full Diamond**, 3 Diamond pieces, 2 Diamond pieces, or 1 Diamond piece (e.g., Diamond chestplate with iron leggings or just a diamond helmet).
  - **Randomized Weapons**: Mobs can spawn with a Diamond Sword, Iron Sword, or a **Copper Sword** (e.g., Full Diamond armor with a Copper Sword!).
  - +65% Health, +20% Speed, +40% Attack Damage.
- **Tier 6 (Days 300+) - Nightmare Era**:
  - Diamond & Netherite armor chances. Netherite, Diamond, and Copper weapons.
  - +100% Health, +25% Speed, +60% Attack Damage.

### 3. Full Moon Event & Sleep Prohibition
- **Sleep Disabled**: When a player attempts to sleep during a Full Moon night, sleep is canceled with an ominous chime:
  > *"You cannot sleep during the Full Moon! The monsters are restless tonight..."*
- **+1 Tier Difficulty Boost**: All monsters receive a +1 Tier difficulty bump during a Full Moon night.
- **Enraged Hostiles**: Hostile mobs spawn with potion buffs (Speed I, Strength I, or Resistance I).
- **Blood Moon Warning**: At dusk (tick 12500) of a Full Moon, a raid horn sounds server-wide with a menacing on-screen Title alert.

---

## 🎮 Commands & Permissions

| Command | Permission | Description |
| :--- | :--- | :--- |
| `/day` or `/daycounter` | Everyone | Shows current day, time of day, moon phase, and difficulty tier. |
| `/sd status` | Everyone | Displays detailed difficulty multipliers and active mob scaling settings. |
| `/sd setday <number>` | OP (Level 2) | Sets the world day offset (e.g. `/sd setday 100` or `/sd setday 200` for testing). |
| `/sd resetday` | OP (Level 2) | Resets the day offset back to natural game time. |
| `/sd toggle` | OP (Level 2) | Enables or disables smart difficulty scaling on the fly. |
| `/sd reload` | OP (Level 2) | Reloads `config/smartdifficulty.json` from disk. |

*(Alias: `/smartdifficulty`)*

---

## ⚙️ Configuration (`config/smartdifficulty.json`)

```json
{
  "enabled": true,
  "dayOffset": 0,
  "preventSleepOnFullMoon": true,
  "broadcastDayChanges": true,
  "broadcastFullMoon": true,
  "mobEquipmentDropChance": 0.05,
  "buffMobAttributes": true,
  "buffMobEffects": true,
  "day100IronChance": 0.85,
  "day200DiamondSlotChance": 0.40
}
```

- `dayOffset`: Number of days to add/subtract from natural world time.
- `preventSleepOnFullMoon`: Prohibits sleeping in beds during Full Moon nights.
- `mobEquipmentDropChance`: Chance (0.05 = 5%) for mobs to drop their equipped armor or weapons upon death.

---

## 🔨 Building from Source

To compile the mod from source code:

```bash
cd smartdifficulty
./build.sh
```

The script compiles the Java sources and packages `smart-difficulty-1.0.0.jar`.
