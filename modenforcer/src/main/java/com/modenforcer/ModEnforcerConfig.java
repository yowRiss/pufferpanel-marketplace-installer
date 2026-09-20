package com.modenforcer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ModEnforcerConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Path configPath;
    private static ModEnforcerConfig instance;

    public boolean enabled = true;
    public int checkDelaySeconds = 3;
    public String kickTitle = "§c§l[Required Mods Missing]";
    public String kickHeader = "§fTo play on this server, you must install the following client mod(s):";
    public String kickFooter = "§7Please install the required mod(s) and rejoin the server.";
    public List<RequiredMod> requiredMods = new ArrayList<>();
    public List<String> suppressedLogPatterns = new ArrayList<>();

    // Real Tab Ping Settings
    public boolean tabPingEnabled = true;
    public int tabPingIntervalTicks = 20;
    public boolean fastPingRefresh = true;
    public int pingRefreshIntervalMs = 1000;

    public static class RequiredMod {
        public String id;
        public String name;
        public String channel;
        public String url;
        public boolean enabled;

        public RequiredMod() {}

        public RequiredMod(String id, String name, String channel, String url, boolean enabled) {
            this.id = id;
            this.name = name;
            this.channel = channel;
            this.url = url;
            this.enabled = enabled;
        }
    }

    public static ModEnforcerConfig get() {
        if (instance == null) {
            instance = new ModEnforcerConfig();
            instance.initDefaults();
        }
        return instance;
    }

    public static void reload() {
        if (configPath != null) {
            load(configPath);
        }
    }

    public static void load(Path path) {
        if (path != null) {
            configPath = path;
        }
        if (configPath != null && Files.exists(configPath)) {
            try (Reader reader = Files.newBufferedReader(configPath)) {
                instance = GSON.fromJson(reader, ModEnforcerConfig.class);
                if (instance == null) {
                    instance = new ModEnforcerConfig();
                    instance.initDefaults();
                    instance.save();
                }
            } catch (Exception e) {
                System.err.println("[ModEnforcer] Failed to load config, using defaults: " + e.getMessage());
                instance = new ModEnforcerConfig();
                instance.initDefaults();
            }
        } else {
            instance = new ModEnforcerConfig();
            instance.initDefaults();
            instance.save();
        }
    }

    private void initDefaults() {
        if (requiredMods == null) {
            requiredMods = new ArrayList<>();
        }
        if (requiredMods.isEmpty()) {
            requiredMods.add(new RequiredMod("voicechat", "Simple Voice Chat", "voicechat:secret", "https://modrinth.com/mod/simple-voice-chat", true));
            requiredMods.add(new RequiredMod("waystones", "Waystones", "waystones:sync_waystones", "https://modrinth.com/mod/waystones", false));
            requiredMods.add(new RequiredMod("jade", "Jade", "jade:request_data", "https://modrinth.com/mod/jade", false));
        }
        if (suppressedLogPatterns == null) {
            suppressedLogPatterns = new ArrayList<>();
        }
        if (suppressedLogPatterns.isEmpty()) {
            suppressedLogPatterns.add("standing on air - force-sending blocks below");
            suppressedLogPatterns.add("Mismatch in destroy block pos");
        }
    }

    public synchronized void save() {
        if (configPath == null) return;
        try {
            Files.createDirectories(configPath.getParent());
            try (Writer writer = Files.newBufferedWriter(configPath)) {
                GSON.toJson(this, writer);
            }
        } catch (Exception e) {
            System.err.println("[ModEnforcer] Failed to save config: " + e.getMessage());
        }
    }

    public boolean toggleMaster() {
        this.enabled = !this.enabled;
        save();
        return this.enabled;
    }

    public boolean toggleTabPing() {
        this.tabPingEnabled = !this.tabPingEnabled;
        save();
        return this.tabPingEnabled;
    }

    public boolean toggleMod(String id) {
        for (RequiredMod mod : requiredMods) {
            if (mod.id.equalsIgnoreCase(id)) {
                mod.enabled = !mod.enabled;
                save();
                return mod.enabled;
            }
        }
        return false;
    }

    public RequiredMod getMod(String id) {
        for (RequiredMod mod : requiredMods) {
            if (mod.id.equalsIgnoreCase(id)) {
                return mod;
            }
        }
        return null;
    }

    public void addMod(RequiredMod mod) {
        requiredMods.removeIf(m -> m.id.equalsIgnoreCase(mod.id));
        requiredMods.add(mod);
        save();
    }

    public boolean removeMod(String id) {
        boolean removed = requiredMods.removeIf(m -> m.id.equalsIgnoreCase(id));
        if (removed) {
            save();
        }
        return removed;
    }
}
