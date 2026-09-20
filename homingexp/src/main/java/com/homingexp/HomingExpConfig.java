package com.homingexp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class HomingExpConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = new File("config/homingexp.json");
    private static final File PLAYERS_FILE = new File("config/homingexp/players.json");

    // Global settings
    public boolean enabled = true;
    public String defaultMode = "homing"; // "homing", "direct", "hybrid"
    public double maxRange = 64.0;
    public double homingSpeed = 1.8;
    public boolean showParticles = true;
    public boolean magnetLooseXp = true;
    public double looseXpRange = 48.0;
    public double hybridDirectDistance = 24.0;

    // Per-player preferences
    private static final Map<UUID, PlayerPref> playerPrefs = new ConcurrentHashMap<>();

    public static class PlayerPref {
        public boolean enabled = true;
        public String mode = null; // null means use defaultMode
    }

    private static HomingExpConfig instance = new HomingExpConfig();

    public static HomingExpConfig get() {
        return instance;
    }

    public static void load() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                HomingExpConfig loaded = GSON.fromJson(reader, HomingExpConfig.class);
                if (loaded != null) {
                    instance = loaded;
                }
            } catch (Exception e) {
                System.err.println("[HomingExp] Failed to load config: " + e.getMessage());
            }
        } else {
            save();
        }

        if (PLAYERS_FILE.exists()) {
            try (FileReader reader = new FileReader(PLAYERS_FILE)) {
                Map<String, PlayerPref> map = GSON.fromJson(reader, new TypeToken<Map<String, PlayerPref>>(){}.getType());
                if (map != null) {
                    playerPrefs.clear();
                    for (Map.Entry<String, PlayerPref> entry : map.entrySet()) {
                        try {
                            playerPrefs.put(UUID.fromString(entry.getKey()), entry.getValue());
                        } catch (Exception ignored) {}
                    }
                }
            } catch (Exception e) {
                System.err.println("[HomingExp] Failed to load player preferences: " + e.getMessage());
            }
        }
    }

    public static synchronized void save() {
        try {
            if (CONFIG_FILE.getParentFile() != null) {
                CONFIG_FILE.getParentFile().mkdirs();
            }
            try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
                GSON.toJson(instance, writer);
            }
        } catch (Exception e) {
            System.err.println("[HomingExp] Failed to save config: " + e.getMessage());
        }
    }

    public static synchronized void savePlayerPrefs() {
        try {
            if (PLAYERS_FILE.getParentFile() != null) {
                PLAYERS_FILE.getParentFile().mkdirs();
            }
            try (FileWriter writer = new FileWriter(PLAYERS_FILE)) {
                Map<String, PlayerPref> map = new ConcurrentHashMap<>();
                for (Map.Entry<UUID, PlayerPref> entry : playerPrefs.entrySet()) {
                    map.put(entry.getKey().toString(), entry.getValue());
                }
                GSON.toJson(map, writer);
            }
        } catch (Exception e) {
            System.err.println("[HomingExp] Failed to save player preferences: " + e.getMessage());
        }
    }

    public boolean isPlayerEnabled(UUID uuid) {
        if (!enabled) return false;
        PlayerPref pref = playerPrefs.get(uuid);
        if (pref != null) {
            return pref.enabled;
        }
        return true;
    }

    public void setPlayerEnabled(UUID uuid, boolean playerEnabled) {
        PlayerPref pref = playerPrefs.computeIfAbsent(uuid, k -> new PlayerPref());
        pref.enabled = playerEnabled;
        savePlayerPrefs();
    }

    public String getPlayerMode(UUID uuid) {
        PlayerPref pref = playerPrefs.get(uuid);
        if (pref != null && pref.mode != null && !pref.mode.trim().isEmpty()) {
            return pref.mode.toLowerCase();
        }
        return defaultMode.toLowerCase();
    }

    public void setPlayerMode(UUID uuid, String mode) {
        PlayerPref pref = playerPrefs.computeIfAbsent(uuid, k -> new PlayerPref());
        pref.mode = mode.toLowerCase();
        savePlayerPrefs();
    }
}
