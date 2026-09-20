package com.autoclicker;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AutoClickManager {
    private static final Map<UUID, AutoClickSession> sessions = new ConcurrentHashMap<>();

    public static AutoClickSession getSession(UUID uuid) {
        return sessions.get(uuid);
    }

    public static boolean isActive(UUID uuid) {
        return sessions.containsKey(uuid);
    }

    public static AutoClickSession start(ServerPlayer player, AutoClickSession.Mode mode, int interval) {
        UUID uuid = player.getUUID();
        AutoClickSession existing = sessions.get(uuid);
        if (existing != null) {
            existing.cleanup(player);
            existing.setMode(mode);
            existing.setCustomAttackInterval(interval);
            return existing;
        }

        AutoClickSession session = new AutoClickSession(uuid, mode, interval);
        sessions.put(uuid, session);
        return session;
    }

    public static boolean stop(UUID uuid) {
        AutoClickSession session = sessions.remove(uuid);
        return session != null;
    }

    public static boolean stop(ServerPlayer player) {
        AutoClickSession session = sessions.remove(player.getUUID());
        if (session != null) {
            session.cleanup(player);
            player.sendSystemMessage(Component.literal("§c[AutoClicker] Auto Clicker dinonaktifkan."));
            return true;
        }
        return false;
    }

    public static void onPlayerDisconnect(ServerPlayer player) {
        AutoClickSession session = sessions.remove(player.getUUID());
        if (session != null) {
            session.cleanup(player);
        }
    }

    public static void stopAll() {
        sessions.clear();
    }

    public static void tick(MinecraftServer server) {
        if (sessions.isEmpty()) {
            return;
        }

        for (UUID uuid : new ArrayList<>(sessions.keySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null || !player.isAlive() || player.isRemoved()) {
                AutoClickSession s = sessions.remove(uuid);
                if (s != null && player != null) {
                    s.cleanup(player);
                }
                continue;
            }

            AutoClickSession session = sessions.get(uuid);
            if (session != null) {
                session.tick(player);
            }
        }
    }
}
