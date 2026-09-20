package com.notooexpensive;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

public class NoTooExpensiveConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = new File("config/notooexpensive.json");

    // Maximum anvil cost in levels.
    // 39 is the vanilla client maximum (costs >= 40 cause "Too Expensive!" on vanilla clients).
    public int maxAnvilCost = 39;

    // Maximum prior work penalty in levels.
    // In vanilla: 0 -> 1 -> 3 -> 7 -> 15 -> 31 -> 63...
    // Capping at 31 prevents items from accumulating unbounded exponential repair penalties.
    public int maxPriorWorkPenalty = 31;

    private static NoTooExpensiveConfig instance = new NoTooExpensiveConfig();

    public static NoTooExpensiveConfig get() {
        return instance;
    }

    public static int getMaxAnvilCost() {
        return Math.max(1, instance.maxAnvilCost);
    }

    public static int getMaxPriorWorkPenalty() {
        return Math.max(0, instance.maxPriorWorkPenalty);
    }

    public static void load() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                NoTooExpensiveConfig loaded = GSON.fromJson(reader, NoTooExpensiveConfig.class);
                if (loaded != null) {
                    instance = loaded;
                }
            } catch (Exception e) {
                System.err.println("[NoTooExpensive] Failed to load config: " + e.getMessage());
            }
        } else {
            save();
        }
    }

    public static void save() {
        try {
            File parent = CONFIG_FILE.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
                GSON.toJson(instance, writer);
            }
        } catch (Exception e) {
            System.err.println("[NoTooExpensive] Failed to save config: " + e.getMessage());
        }
    }
}
