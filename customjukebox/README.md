# CustomJukebox (Fabric Server-Side Mod)

[![Platform: Fabric](https://img.shields.io/badge/Platform-Fabric-blue.svg)](https://fabricmc.net/)
[![Minecraft: 1.21+ / 26.3](https://img.shields.io/badge/Minecraft-1.21%2B%20%2F%2026.3-brightgreen.svg)](https://minecraft.net/)
[![Side: Server-Only](https://img.shields.io/badge/Side-Server--Only-orange.svg)]()

**CustomJukebox** is a lightweight, 100% server-side Fabric mod for modern Minecraft servers that enables true custom music discs played through vanilla Jukeboxes with 3D locational audio, automated background downloaders, and seamless track looping.

Players do **not** need any custom client mod or resource packs. Audio is streamed locationally using the [Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat) server audio API directly at the jukebox's coordinates.

---

## 🌟 Key Features

### 1. 3D Locational Jukebox Audio
- **True In-World Sound Source**: Audio emits in full 3D space directly from the jukebox block position (`x, y, z`).
- **Distance Attenuation**: Sound smoothly fades over distance naturally just like vanilla jukebox discs.
- **Powered by Simple Voice Chat**: Integrates cleanly with Voice Chat's audio engine without client-side modifications.

### 2. Seamless Auto-Looping
- When a song finishes playing in a jukebox, it automatically loops and restarts from the beginning without stopping or requiring players to re-insert the disc.

### 3. Silent Disc Eject & Operation
- Completely silences log and chat notifications when discs are ejected, stopped, or inserted, keeping your server console and player chat clean and spam-free.

### 4. Background Throttled Downloader (`/musicdisc add <url>`)
- **Direct Web Ingestion**: Add songs on the fly by pasting any direct `.mp3` URL.
- **Bandwidth Throttling (256 KB/s)**: Low-speed download rate limit prevents network choking and TPS drops while downloading large files.
- **Live ETA Whisper**: Calculates remaining download time based on `Content-Length` and whispers real-time status and estimated arrival directly to the player.
- **Auto Delivery**: Automatically mints and deposits the newly created music disc straight into the player's inventory upon download completion.

### 5. Dedicated Music Library & Custom Discs
- Scans and loads tracks placed in the server's `music/` directory (`.mp3`, `.wav`).
- Discs are tagged with custom NBT/data-components containing track ID, artist, and track title.

---

## 🎮 Commands & Permissions

| Command | Permission | Description |
| :--- | :--- | :--- |
| `/musicdisc list` | Everyone | Lists all available custom music tracks loaded in the server library. |
| `/givemusic <track>` | OP / Admin | Spawns a custom music disc for the specified track directly into your inventory. |
| `/givemusic <player> <track>` | OP / Admin | Gives a custom music disc to the targeted player. |
| `/musicdisc add <url>` | OP / Admin | Downloads an MP3 track in the background with speed throttling, whispers ETA, and gives the disc. |

---

## 📁 Directory Structure & Audio Files

On server startup, the mod ensures a `music/` directory exists in the server root:

```text
minecraft-server/
├── config/
├── mods/
│   └── custom-jukebox-1.1.0.jar
└── music/
    ├── epic_adventure.mp3
    ├── lo_fi_beats.mp3
    └── boss_fight.wav
```

You can upload `.mp3` or `.wav` files directly into `music/` or use `/musicdisc add <url>` in-game.

---

## 🔨 Building from Source

To compile the mod from source code:

```bash
cd customjukebox
./build.sh
```

The script compiles the Java sources and packages `custom-jukebox-1.1.0.jar`.
