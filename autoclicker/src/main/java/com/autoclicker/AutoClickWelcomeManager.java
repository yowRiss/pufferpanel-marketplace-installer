package com.autoclicker;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AutoClickWelcomeManager {
    private static final File FILE = new File("config/autoclicker_first_join.json");
    private static final Gson GSON = new Gson();
    private static final Set<UUID> seenPlayers = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final Map<UUID, Integer> pendingPopups = new ConcurrentHashMap<>();

    public static void init() {
        if (FILE.exists()) {
            try (FileReader reader = new FileReader(FILE)) {
                List<String> list = GSON.fromJson(reader, new TypeToken<List<String>>(){}.getType());
                if (list != null) {
                    for (String s : list) {
                        try {
                            seenPlayers.add(UUID.fromString(s));
                        } catch (Exception ignored) {}
                    }
                }
            } catch (Exception e) {
                System.err.println("[AutoClicker] Failed to load first join data: " + e.getMessage());
            }
        }
    }

    public static synchronized void save() {
        try {
            if (FILE.getParentFile() != null) {
                FILE.getParentFile().mkdirs();
            }
            try (FileWriter writer = new FileWriter(FILE)) {
                List<String> list = new ArrayList<>();
                for (UUID u : seenPlayers) {
                    list.add(u.toString());
                }
                GSON.toJson(list, writer);
            }
        } catch (Exception e) {
            System.err.println("[AutoClicker] Failed to save first join data: " + e.getMessage());
        }
    }

    public static boolean hasSeen(UUID uuid) {
        return seenPlayers.contains(uuid);
    }

    public static void markSeen(UUID uuid) {
        seenPlayers.add(uuid);
        save();
    }

    public static void onPlayerJoin(ServerPlayer player) {
        UUID uuid = player.getUUID();
        if (!hasSeen(uuid)) {
            // Schedule popup 50 ticks (2.5 seconds) after join
            pendingPopups.put(uuid, 50);
        }
    }

    public static void onPlayerDisconnect(ServerPlayer player) {
        if (player != null) {
            pendingPopups.remove(player.getUUID());
        }
    }

    public static void tick(MinecraftServer server) {
        if (pendingPopups.isEmpty()) return;

        for (Map.Entry<UUID, Integer> entry : new ArrayList<>(pendingPopups.entrySet())) {
            UUID uuid = entry.getKey();
            int delay = entry.getValue() - 1;
            if (delay <= 0) {
                pendingPopups.remove(uuid);
                ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                if (player != null && player.isAlive()) {
                    showPopup(player);
                    markSeen(uuid);
                }
            } else {
                pendingPopups.put(uuid, delay);
            }
        }
    }

    public static void showPopup(ServerPlayer player) {
        try {
            // Animation: 10 ticks fade-in (0.5s), 100 ticks stay (5s), 20 ticks fade-out (1s)
            player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 100, 20));
            player.connection.send(new ClientboundSetTitleTextPacket(Component.literal("§6§lAutoClicker Mod")));
            player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("§eUse §a/ac help §eto use autoclicker mod")));
            player.playSound(SoundEvents.PLAYER_LEVELUP, 0.8f, 1.2f);
        } catch (Exception e) {
            System.err.println("[AutoClicker] Failed to show popup to player: " + e.getMessage());
        }
    }
}
