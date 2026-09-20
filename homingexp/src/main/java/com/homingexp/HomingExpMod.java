package com.homingexp;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;

public class HomingExpMod implements ModInitializer {

    @Override
    public void onInitialize() {
        System.out.println("[HomingExp] Initializing Homing Experience Mod...");
        HomingExpConfig.load();

        // Register in-game commands
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            HomingExpCommands.register(dispatcher);
        });

        // Register combat kill event (fires right when killer kills victim)
        ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((world, killer, victim, damageSource) -> {
            try {
                HomingExpManager.onMobKilled(world, killer, victim, damageSource);
            } catch (Exception e) {
                System.err.println("[HomingExp] Error processing combat kill: " + e.getMessage());
            }
        });

        // Fallback death event (for projectile or indirect kills if needed)
        ServerLivingEntityEvents.AFTER_DEATH.register((victim, damageSource) -> {
            try {
                if (victim != null && victim.level() instanceof ServerLevel serverLevel) {
                    if (!victim.wasExperienceConsumed()) {
                        HomingExpManager.onMobKilled(serverLevel, damageSource != null ? damageSource.getEntity() : null, victim, damageSource);
                    }
                }
            } catch (Exception e) {
                System.err.println("[HomingExp] Error processing entity death: " + e.getMessage());
            }
        });

        // Track entity load & unload for orb magnet and cleanup
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            HomingExpManager.onEntityLoad(entity, world);
        });

        ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            HomingExpManager.onEntityUnload(entity, world);
        });

        // Server tick loop for smooth homing physics and loose orb magnet
        ServerTickEvents.END_LEVEL_TICK.register(world -> {
            try {
                HomingExpManager.onWorldTick(world);
            } catch (Exception e) {
                // Safeguard against tick error
            }
        });

        System.out.println("[HomingExp] Homing Experience Mod initialized successfully!");
    }
}
