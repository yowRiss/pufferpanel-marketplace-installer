package com.customjukebox;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;

public class JukeboxConfig {
    private static final File CONFIG_FILE = new File("config/customjukebox.properties");
    private static String mode = "direct"; // "direct" (Studio Master) or "3d" (OpenAL Positional)
    private static int volume = 65; // 1 - 100 (65 gives ~3.7 dB headroom against Opus bass clipping)
    private static double range = 64.0; // hearing radius in blocks
    private static boolean loop = true;

    public static void load() {
        if (!CONFIG_FILE.getParentFile().exists()) {
            CONFIG_FILE.getParentFile().mkdirs();
        }
        if (CONFIG_FILE.exists()) {
            try (FileInputStream in = new FileInputStream(CONFIG_FILE)) {
                Properties props = new Properties();
                props.load(in);
                String m = props.getProperty("mode", "direct").trim().toLowerCase();
                mode = "3d".equals(m) ? "3d" : "direct";
                try {
                    volume = Integer.parseInt(props.getProperty("volume", "65").trim());
                    if (volume < 1) volume = 1;
                    if (volume > 100) volume = 100;
                } catch (NumberFormatException ignored) {}
                try {
                    range = Double.parseDouble(props.getProperty("range", "64.0").trim());
                    if (range < 4.0) range = 4.0;
                    if (range > 256.0) range = 256.0;
                } catch (NumberFormatException ignored) {}
                loop = Boolean.parseBoolean(props.getProperty("loop", "true").trim());
            } catch (Exception e) {
                System.err.println("[CustomJukebox] Failed to load config: " + e.getMessage());
            }
        } else {
            save();
        }
    }

    public static void save() {
        try {
            if (!CONFIG_FILE.getParentFile().exists()) {
                CONFIG_FILE.getParentFile().mkdirs();
            }
            Properties props = new Properties();
            props.setProperty("mode", mode);
            props.setProperty("volume", String.valueOf(volume));
            props.setProperty("range", String.valueOf(range));
            props.setProperty("loop", String.valueOf(loop));
            try (FileOutputStream out = new FileOutputStream(CONFIG_FILE)) {
                props.store(out, "CustomJukebox Configuration\n"
                    + "# mode: 'direct' (Studio Master - crisp, clean, no tin-can effect) or '3d' (OpenAL Positional)\n"
                    + "# volume: 1 to 100 (65 recommended to prevent bass clipping on Opus)\n"
                    + "# range: hearing radius in blocks\n"
                    + "# loop: loop track when finished (true/false)");
            }
        } catch (Exception e) {
            System.err.println("[CustomJukebox] Failed to save config: " + e.getMessage());
        }
    }

    public static String getMode() { return mode; }
    public static void setMode(String newMode) {
        mode = "3d".equalsIgnoreCase(newMode) ? "3d" : "direct";
        save();
    }

    public static int getVolume() { return volume; }
    public static void setVolume(int newVol) {
        if (newVol < 1) newVol = 1;
        if (newVol > 100) newVol = 100;
        volume = newVol;
        save();
    }

    public static double getRange() { return range; }
    public static void setRange(double newRange) {
        if (newRange < 4.0) newRange = 4.0;
        if (newRange > 256.0) newRange = 256.0;
        range = newRange;
        save();
    }

    public static boolean isLoop() { return loop; }
    public static void setLoop(boolean newLoop) {
        loop = newLoop;
        save();
    }
}
