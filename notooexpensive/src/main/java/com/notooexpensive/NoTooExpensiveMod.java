package com.notooexpensive;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

public class NoTooExpensiveMod implements ModInitializer {

    @Override
    public void onInitialize() {
        System.out.println("[NoTooExpensive] Initializing NoTooExpensive Mod...");
        NoTooExpensiveConfig.load();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            NoTooExpensiveCommands.register(dispatcher);
        });

        System.out.println("[NoTooExpensive] Initialized successfully! Max Anvil Cost: " 
            + NoTooExpensiveConfig.getMaxAnvilCost() 
            + ", Max Prior Work Penalty: " 
            + NoTooExpensiveConfig.getMaxPriorWorkPenalty());
    }
}
