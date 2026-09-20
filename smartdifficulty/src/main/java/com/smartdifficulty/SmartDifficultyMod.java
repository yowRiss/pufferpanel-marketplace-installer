package com.smartdifficulty;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.world.entity.Mob;

public class SmartDifficultyMod implements ModInitializer {

    @Override
    public void onInitialize() {
        System.out.println("[SmartDifficulty] Initializing Smart World Difficulty & Day Counter...");
        SmartDifficultyConfig.load();

        // Register commands (/day, /sd)
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            SmartDifficultyCommands.register(dispatcher);
        });

        // Register Full Moon sleep prohibition
        EntitySleepEvents.ALLOW_SLEEPING.register((player, bedPos) -> {
            return SmartDifficultyManager.checkSleep(player, bedPos);
        });

        // Register Mob Scaling on entity spawn/load
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof Mob mob) {
                SmartDifficultyManager.scaleMob(mob, world);
            }
        });

        // Register Level Tick for Day Rollover and Full Moon Rise announcements
        ServerTickEvents.END_LEVEL_TICK.register(world -> {
            try {
                SmartDifficultyManager.onLevelTick(world);
            } catch (Exception ignored) {}
        });

        System.out.println("[SmartDifficulty] Smart World Difficulty & Day Counter initialized successfully!");
    }
}
