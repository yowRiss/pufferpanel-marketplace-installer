package com.customjukebox;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class CustomJukeboxMod implements ModInitializer {
    public static final String MOD_ID = "customjukebox";

    @Override
    public void onInitialize() {
        System.out.println("[CustomJukebox] Initializing Custom Jukebox Music Disc Mod...");

        JukeboxMusicManager.init();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            JukeboxCommands.register(dispatcher);
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            JukeboxMusicManager.stopAll();
        });

        UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
            if (hand != InteractionHand.MAIN_HAND || level.isClientSide()) {
                return InteractionResult.PASS;
            }

            BlockPos pos = hitResult.getBlockPos();
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof JukeboxBlock)) {
                return InteractionResult.PASS;
            }

            ItemStack held = player.getItemInHand(hand);
            CustomData customData = held.get(DataComponents.CUSTOM_DATA);

            // Case 1: Player is holding a custom music disc and right-clicks the jukebox
            if (customData != null && customData.copyTag().contains("custom_music")) {
                String trackFile = customData.copyTag().getString("custom_music").orElse("");
                String title = customData.copyTag().contains("custom_title") ?
                               customData.copyTag().getString("custom_title").orElse("") : JukeboxCommands.cleanTitle(trackFile);

                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof JukeboxBlockEntity jbe) {
                    if (JukeboxMusicManager.isPlaying(pos)) {
                        JukeboxMusicManager.stop(pos);
                    }
                    if (state.getValue(JukeboxBlock.HAS_RECORD)) {
                        jbe.popOutTheItem();
                    }

                    ItemStack toInsert = held.copyWithCount(1);
                    jbe.setSongItemWithoutPlaying(toInsert);
                    level.setBlock(pos, state.setValue(JukeboxBlock.HAS_RECORD, true), 3);

                    if (!player.isCreative()) {
                        held.shrink(1);
                    }

                    boolean ok = JukeboxMusicManager.play(level, pos, trackFile, title);
                    if (!ok) {
                        player.sendSystemMessage(Component.literal("§c[Jukebox] Audio file '" + trackFile + "' could not be played. Check server 'music/' folder."));
                    }

                    return InteractionResult.SUCCESS;
                }
            }

            // Case 2: Jukebox has a record and player interacts to eject it
            if (state.getValue(JukeboxBlock.HAS_RECORD)) {
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof JukeboxBlockEntity jbe) {
                    ItemStack inside = jbe.getTheItem();
                    CustomData insideData = inside.get(DataComponents.CUSTOM_DATA);
                    boolean isCustom = insideData != null && insideData.copyTag().contains("custom_music");

                    if (isCustom || JukeboxMusicManager.isPlaying(pos)) {
                        if (JukeboxMusicManager.isPlaying(pos)) {
                            JukeboxMusicManager.stop(pos);
                        }
                        jbe.popOutTheItem();
                        level.setBlock(pos, state.setValue(JukeboxBlock.HAS_RECORD, false), 3);
                        return InteractionResult.SUCCESS;
                    }
                }
            }

            return InteractionResult.PASS;
        });

        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
            if (state.getBlock() instanceof JukeboxBlock) {
                if (JukeboxMusicManager.isPlaying(pos)) {
                    JukeboxMusicManager.stop(pos);
                }
            }
            return true;
        });

        System.out.println("[CustomJukebox] Custom Jukebox Music Disc Mod initialized successfully!");
    }
}
