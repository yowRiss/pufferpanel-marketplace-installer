package com.modenforcer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TabPingManager {
    private static final Map<UUID, Integer> LAST_PING = new ConcurrentHashMap<>();
    private static int tickCounter = 0;

    public static Component getDisplayName(ServerPlayer player) {
        ModEnforcerConfig config = ModEnforcerConfig.get();
        if (!config.tabPingEnabled) {
            return null; // Return null so vanilla uses standard team-formatted name
        }

        int ping = (player.connection != null) ? player.connection.latency() : 0;
        if (ping < 0) {
            ping = 0;
        }

        ChatFormatting color = getPingColor(ping);
        MutableComponent pingComp = Component.literal(" [").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(ping + "ms").withStyle(color))
                .append(Component.literal("]").withStyle(ChatFormatting.GRAY));

        // Use player.getDisplayName() which formats team color/prefix/suffix
        return player.getDisplayName().copy().append(pingComp);
    }

    public static ChatFormatting getPingColor(int ping) {
        if (ping <= 75) {
            return ChatFormatting.GREEN;
        } else if (ping <= 150) {
            return ChatFormatting.YELLOW;
        } else if (ping <= 250) {
            return ChatFormatting.GOLD;
        } else if (ping <= 400) {
            return ChatFormatting.RED;
        } else {
            return ChatFormatting.DARK_RED;
        }
    }

    public static void tick(MinecraftServer server) {
        ModEnforcerConfig config = ModEnforcerConfig.get();
        if (!config.tabPingEnabled) {
            return;
        }

        tickCounter++;
        if (tickCounter < Math.max(1, config.tabPingIntervalTicks)) {
            return;
        }
        tickCounter = 0;

        if (server.getPlayerList() == null) {
            return;
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.connection == null) {
                continue;
            }
            int ping = player.connection.latency();
            if (ping < 0) ping = 0;

            Integer last = LAST_PING.get(player.getUUID());
            if (last == null || last != ping) {
                LAST_PING.put(player.getUUID(), ping);
                server.getPlayerList().broadcastAll(
                        new ClientboundPlayerInfoUpdatePacket(
                                ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME,
                                player
                        )
                );
            }
        }
    }

    public static void onPlayerJoin(ServerPlayer player, MinecraftServer server) {
        if (ModEnforcerConfig.get().tabPingEnabled && server.getPlayerList() != null) {
            int ping = (player.connection != null) ? player.connection.latency() : 0;
            if (ping < 0) ping = 0;
            LAST_PING.put(player.getUUID(), ping);
            server.getPlayerList().broadcastAll(
                    new ClientboundPlayerInfoUpdatePacket(
                            ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME,
                            player
                    )
            );
        }
    }

    public static void onPlayerDisconnect(UUID uuid) {
        LAST_PING.remove(uuid);
    }

    public static void broadcastUpdateAll(MinecraftServer server) {
        LAST_PING.clear();
        if (server == null || server.getPlayerList() == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.connection != null) {
                int ping = player.connection.latency();
                if (ping < 0) ping = 0;
                LAST_PING.put(player.getUUID(), ping);
            }
            server.getPlayerList().broadcastAll(
                    new ClientboundPlayerInfoUpdatePacket(
                            ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME,
                            player
                    )
            );
        }
    }

    public static void broadcastResetAll(MinecraftServer server) {
        LAST_PING.clear();
        if (server == null || server.getPlayerList() == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            server.getPlayerList().broadcastAll(
                    new ClientboundPlayerInfoUpdatePacket(
                            ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME,
                            player
                    )
            );
        }
    }
}
