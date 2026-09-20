package com.autoclicker;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

public class AutoClickerMod implements ModInitializer {
    public static final String MOD_ID = "autoclicker";

    @Override
    public void onInitialize() {
        System.out.println("[AutoClicker] Initializing Server-side Auto Clicker Mod...");

        AutoClickWelcomeManager.init();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            AutoClickCommands.register(dispatcher);
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            AutoClickWelcomeManager.onPlayerJoin(handler.getPlayer());
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            AutoClickManager.tick(server);
            AutoClickWelcomeManager.tick(server);
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            AutoClickManager.onPlayerDisconnect(handler.getPlayer());
            AutoClickWelcomeManager.onPlayerDisconnect(handler.getPlayer());
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            AutoClickManager.stopAll();
        });

        System.out.println("[AutoClicker] Server-side Auto Clicker Mod initialized successfully!");
    }
}
