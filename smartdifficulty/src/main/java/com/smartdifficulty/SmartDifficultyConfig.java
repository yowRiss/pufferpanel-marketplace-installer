package com.smartdifficulty;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

public class SmartDifficultyConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = new File("config/smartdifficulty.json");

    public boolean enabled = true;
    public long dayOffset = 0;
    public boolean preventSleepOnFullMoon = true;
    public boolean broadcastDayChanges = true;
    public boolean broadcastFullMoon = true;
    public float mobEquipmentDropChance = 0.05f;
    public boolean buffMobAttributes = true;
    public boolean buffMobEffects = true;
    public double day100IronChance = 0.85;
    public double day200DiamondSlotChance = 0.40;

    private static SmartDifficultyConfig instance = new SmartDifficultyConfig();

    public static SmartDifficultyConfig get() {
        return instance;
    }

    public static void load() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                SmartDifficultyConfig loaded = GSON.fromJson(reader, SmartDifficultyConfig.class);
                if (loaded != null) {
                    instance = loaded;
                }
            } catch (Exception e) {
                System.err.println("[SmartDifficulty] Failed to load config: " + e.getMessage());
            }
        } else {
            save();
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
            System.err.println("[SmartDifficulty] Failed to save config: " + e.getMessage());
        }
    }
}
