# 🔨 NoTooExpensive (100% Server-Side Fabric Mod)

Removes the vanilla anvil **"Too Expensive!"** limitation when combining enchantments and repairing items, allowing players to endlessly enchant and repair equipment without hitting the game-breaking limit.

---

## 🌟 Key Features

- **No "Too Expensive!" Lockout**: Enchantment combining and repair operations never get blocked by the vanilla `>= 40` level cost threshold.
- **100% Server-Side Compatible**: Players on pure vanilla Minecraft clients can connect, combine enchantments, and repair gear without needing any client-side mod or modified client!
- **Prior Work Penalty Cap**: In vanilla, prior work penalty doubles exponentially (`oldCost * 2 + 1 = 1 -> 3 -> 7 -> 15 -> 31 -> 63...`). This mod caps the penalty at 31 levels so items never accumulate infinite repair penalties or integer overflows.
- **Fair & Balanced**: Anvil operations that exceed 39 levels are capped at 39 levels (1,395 XP points), keeping anvil mechanics meaningful while preventing item retirement.
- **Stack Protection Preserved**: Vanilla's check preventing combining full stacks of items in the first anvil slot is preserved.
- **In-Game Commands & Hot-Reload**: Configure maximum anvil cost and maximum penalty on-the-fly without server restarts.

---

## 💻 Commands

| Command | Permission | Description |
|---|---|---|
| `/nte status` | All | View current anvil cost & penalty settings |
| `/nte setcost <1-39>` | OP (Level 2) | Set maximum anvil enchantment cost (default: 39) |
| `/nte setpenalty <penalty>` | OP (Level 2) | Set maximum prior work penalty (default: 31) |
| `/nte reload` | OP (Level 2) | Reload configuration from `config/notooexpensive.json` |

*(Alias: `/notooexpensive`)*

---

## ⚙️ Configuration (`config/notooexpensive.json`)

```json
{
  "maxAnvilCost": 39,
  "maxPriorWorkPenalty": 31
}
```

- `maxAnvilCost`: Maximum level cost for any anvil operation (default: `39`). **Note**: Vanilla clients enforce a client-side block on costs `>= 40`. Keeping this at `39` guarantees vanilla client compatibility.
- `maxPriorWorkPenalty`: Maximum prior work penalty an item can accumulate (default: `31`).

---

## 🛠️ Building from Source

```bash
cd notooexpensive
./build.sh
```
Compiles and outputs `notooexpensive-1.0.0.jar`.
