package com.modenforcer;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ClientboundPlayChannelEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ModEnforcerMod implements ModInitializer {
    public static final String MOD_ID = "modenforcer";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final Map<UUID, Timer> PENDING_CHECKS = new ConcurrentHashMap<>();
    private static final Set<UUID> VERIFIED_PLAYERS = Collections.newSetFromMap(new ConcurrentHashMap<>());

    @Override
    public void onInitialize() {
        Path configPath = FabricLoader.getInstance().getConfigDir().resolve("modenforcer.json");
        ModEnforcerConfig.load(configPath);

        // Install Log Spam Filter
        LogSpamFilter.install();

        // Register OP Commands
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> {
            ModEnforcerCommand.register(dispatcher);
        });

        // Register Server Tick Event for Tab Ping updates
        ServerTickEvents.END_SERVER_TICK.register(TabPingManager::tick);

        // Register Play-Phase Join Event
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            if (player != null) {
                onPlayerJoin(player, server);
                TabPingManager.onPlayerJoin(player, server);
            }
        });

        // Register Channel Registration Events (fired when client sends channel announcements)
        ClientboundPlayChannelEvents.REGISTER.register((handler, sender, server, channels) -> {
            try {
                ServerPlayer player = handler.getPlayer();
                if (player != null && player.connection != null) {
                    checkPlayerVerified(player, server, false);
                }
            } catch (Throwable ignored) {}
        });

        // Cleanup on Player Disconnect
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayer player = handler.getPlayer();
            if (player != null) {
                cancelPendingCheck(player.getUUID());
                VERIFIED_PLAYERS.remove(player.getUUID());
                TabPingManager.onPlayerDisconnect(player.getUUID());
            }
        });

        // Cleanup on Server Stopping
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (Timer timer : PENDING_CHECKS.values()) {
                try {
                    timer.cancel();
                    timer.purge();
                } catch (Exception ignored) {}
            }
            PENDING_CHECKS.clear();
            VERIFIED_PLAYERS.clear();
        });

        LOGGER.info("[ModEnforcer] Initialized successfully. Enforcing client mods with play-phase handshake.");
    }

    private void onPlayerJoin(ServerPlayer player, MinecraftServer server) {
        ModEnforcerConfig config = ModEnforcerConfig.get();
        if (!config.enabled) {
            return;
        }

        UUID uuid = player.getUUID();
        VERIFIED_PLAYERS.remove(uuid);
        cancelPendingCheck(uuid);

        // Immediate check in case channels were already negotiated
        if (checkPlayerVerified(player, server, false)) {
            return;
        }

        // Schedule delayed check to give client time to complete channel registration & handshake
        int delaySeconds = Math.max(1, config.checkDelaySeconds);
        Timer timer = new Timer("ModEnforcer-Timeout-" + player.getGameProfile().name(), true);
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                server.execute(() -> {
                    PENDING_CHECKS.remove(uuid);
                    if (player.connection == null || !player.connection.isAcceptingMessages()) {
                        return;
                    }
                    checkPlayerVerified(player, server, true);
                });
            }
        }, delaySeconds * 1000L);

        PENDING_CHECKS.put(uuid, timer);
    }

    private boolean checkPlayerVerified(ServerPlayer player, MinecraftServer server, boolean isFinalCheck) {
        try {
            ModEnforcerConfig config = ModEnforcerConfig.get();
            if (!config.enabled) {
                return true;
            }

            UUID uuid = player.getUUID();
            if (VERIFIED_PLAYERS.contains(uuid)) {
                return true;
            }

            List<ModEnforcerConfig.RequiredMod> missing = getMissingMods(player);
            if (missing.isEmpty()) {
                VERIFIED_PLAYERS.add(uuid);
                cancelPendingCheck(uuid);
                LOGGER.info("[ModEnforcer] Player {} verified with all required client mods.", player.getGameProfile().name());
                return true;
            }

            if (isFinalCheck) {
                disconnectMissingMods(player, missing);
            }
        } catch (Throwable t) {
            LOGGER.debug("[ModEnforcer] Handled exception during player mod verification: {}", t.getMessage());
        }

        return false;
    }

    private List<ModEnforcerConfig.RequiredMod> getMissingMods(ServerPlayer player) {
        ModEnforcerConfig config = ModEnforcerConfig.get();
        List<ModEnforcerConfig.RequiredMod> missing = new ArrayList<>();

        for (ModEnforcerConfig.RequiredMod mod : config.requiredMods) {
            if (!mod.enabled) {
                continue;
            }
            if (!hasMod(player, mod)) {
                missing.add(mod);
            }
        }
        return missing;
    }

    private boolean hasMod(ServerPlayer player, ModEnforcerConfig.RequiredMod mod) {
        // 1. Simple Voice Chat special handling
        if ("voicechat".equalsIgnoreCase(mod.id) || (mod.channel != null && mod.channel.startsWith("voicechat:"))) {
            if (isVoiceChatPresent(player)) {
                return true;
            }
        }

        // 2. Generic Fabric Play Networking channel check
        if (mod.channel != null && !mod.channel.isBlank()) {
            try {
                Identifier channelId = Identifier.tryParse(mod.channel);
                if (channelId != null && ServerPlayNetworking.canSend(player, channelId)) {
                    return true;
                }
            } catch (Throwable ignored) {}
        }

        return false;
    }

    private boolean isVoiceChatPresent(ServerPlayer player) {
        // Check 1: Simple Voice Chat Server API compatibility check via reflection
        try {
            Class<?> vcClass = Class.forName("de.maxhenkel.voicechat.Voicechat");
            Object server = vcClass.getField("SERVER").get(null);
            if (server != null) {
                Method method = server.getClass().getMethod("isCompatible", ServerPlayer.class);
                Object result = method.invoke(server, player);
                if (Boolean.TRUE.equals(result)) {
                    return true;
                }
            }
        } catch (Throwable ignored) {}

        // Check 2: Fabric Play Networking channel sendable check
        try {
            Identifier secretChannel = Identifier.tryParse("voicechat:secret");
            if (secretChannel != null && ServerPlayNetworking.canSend(player, secretChannel)) {
                return true;
            }
            Identifier playerStateChannel = Identifier.tryParse("voicechat:player_state");
            if (playerStateChannel != null && ServerPlayNetworking.canSend(player, playerStateChannel)) {
                return true;
            }
        } catch (Throwable ignored) {}

        return false;
    }

    private void disconnectMissingMods(ServerPlayer player, List<ModEnforcerConfig.RequiredMod> missing) {
        ModEnforcerConfig config = ModEnforcerConfig.get();
        StringBuilder sb = new StringBuilder();
        sb.append(config.kickTitle).append("\n\n");
        sb.append(config.kickHeader).append("\n\n");

        for (ModEnforcerConfig.RequiredMod m : missing) {
            sb.append("§c✖ §e§l").append(m.name).append("§r\n");
            if (m.url != null && !m.url.isBlank()) {
                sb.append("   §7Download: §b").append(m.url).append("\n");
            }
        }

        sb.append("\n").append(config.kickFooter).append("\n");

        LOGGER.warn("[ModEnforcer] Disconnected player {} for missing required client mods: {}",
                player.getGameProfile().name(),
                missing.stream().map(m -> m.name + " (" + m.channel + ")").toList());

        player.connection.disconnect(Component.literal(sb.toString()));
    }

    private static void cancelPendingCheck(UUID uuid) {
        Timer timer = PENDING_CHECKS.remove(uuid);
        if (timer != null) {
            try {
                timer.cancel();
                timer.purge();
            } catch (Exception ignored) {}
        }
    }
}
