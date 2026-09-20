package com.customjukebox;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class JukeboxCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("givemusic")
            .then(Commands.argument("track", StringArgumentType.greedyString())
                .suggests((ctx, builder) -> {
                    String remaining = builder.getRemainingLowerCase().trim();
                    for (String name : JukeboxMusicManager.getTrackNames()) {
                        if (name.toLowerCase().contains(remaining)) {
                            builder.suggest(name);
                            if (name.contains(" ")) {
                                builder.suggest('"' + name + '"');
                            }
                        }
                    }
                    return builder.buildFuture();
                })
                .executes(ctx -> {
                    CommandSourceStack source = ctx.getSource();
                    ServerPlayer player = source.getPlayerOrException();
                    String track = StringArgumentType.getString(ctx, "track");
                    return giveDisc(source, Collections.singleton(player), track);
                })
            )
        );

        dispatcher.register(Commands.literal("musicdisc")
            .then(Commands.literal("add")
                .then(Commands.argument("url", StringArgumentType.greedyString())
                    .executes(ctx -> {
                        CommandSourceStack source = ctx.getSource();
                        ServerPlayer player = source.getPlayerOrException();
                        String url = StringArgumentType.getString(ctx, "url");
                        return TrackDownloader.startDownload(source, player, url);
                    })
                )
            )
            .then(Commands.literal("give")
                .then(Commands.argument("targets", EntityArgument.players())
                    .then(Commands.argument("track", StringArgumentType.greedyString())
                        .suggests((ctx, builder) -> {
                            String remaining = builder.getRemainingLowerCase().trim();
                            for (String name : JukeboxMusicManager.getTrackNames()) {
                                if (name.toLowerCase().contains(remaining)) {
                                    builder.suggest(name);
                                    if (name.contains(" ")) {
                                        builder.suggest('"' + name + '"');
                                    }
                                }
                            }
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
                            String track = StringArgumentType.getString(ctx, "track");
                            return giveDisc(ctx.getSource(), targets, track);
                        })
                    )
                )
            )
            .then(Commands.literal("list")
                .executes(ctx -> {
                    CommandSourceStack source = ctx.getSource();
                    List<String> tracks = JukeboxMusicManager.getTrackNames();
                    if (tracks.isEmpty()) {
                        source.sendSuccess(() -> Component.literal("§e[MusicDisc] Folder 'music/' is empty! Upload audio files (.mp3, .wav, .ogg, .flac) to server 'music/' folder."), false);
                    } else {
                        source.sendSuccess(() -> Component.literal("§6=== Available Music Discs (" + tracks.size() + ") ==="), false);
                        for (int i = 0; i < tracks.size(); i++) {
                            int idx = i + 1;
                            String t = tracks.get(i);
                            source.sendSuccess(() -> Component.literal(" §7" + idx + ". §e" + t), false);
                        }
                        source.sendSuccess(() -> Component.literal("§7Get disc: §a/givemusic <trackname>§7 or §a/musicdisc give <player> <trackname>"), false);
                        source.sendSuccess(() -> Component.literal("§7Download dari URL: §a/musicdisc add <link_mp3>"), false);
                    }
                    return 1;
                })
            )
            .then(Commands.literal("stop")
                .executes(ctx -> {
                    CommandSourceStack source = ctx.getSource();
                    JukeboxMusicManager.stopAll();
                    source.sendSuccess(() -> Component.literal("§a[MusicDisc] Stopped all active jukebox playback."), true);
                    return 1;
                })
            )
        );
    }

    private static int giveDisc(CommandSourceStack source, Collection<ServerPlayer> targets, String trackInput) {
        File file = JukeboxMusicManager.resolveTrackFile(trackInput);
        if (file == null) {
            source.sendFailure(Component.literal("§c[MusicDisc] Audio file '" + trackInput + "' not found in server 'music/' folder!"));
            source.sendFailure(Component.literal("§7Type §e/musicdisc list§7 to see available tracks."));
            return 0;
        }

        String fileName = file.getName();
        String title = cleanTitle(fileName);

        ItemStack disc = createMusicDisc(fileName, title);

        for (ServerPlayer player : targets) {
            boolean added = player.getInventory().add(disc.copy());
            if (!added) {
                player.drop(disc.copy(), false, net.minecraft.util.Prediction.SERVER_ONLY);
            }
            player.sendSystemMessage(Component.literal("§aReceived Custom Music Disc: §e" + title));
        }

        source.sendSuccess(() -> Component.literal("§aGave §e" + title + "§a music disc to " + targets.size() + " player(s)."), true);
        return targets.size();
    }

    public static ItemStack createMusicDisc(String fileName, String title) {
        ItemStack disc = new ItemStack(Items.MUSIC_DISC_RELIC);
        disc.remove(DataComponents.JUKEBOX_PLAYABLE);

        CompoundTag tag = new CompoundTag();
        tag.putString("custom_music", fileName);
        tag.putString("custom_title", title);

        disc.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        disc.set(DataComponents.CUSTOM_NAME, Component.literal("§b🎵 " + title));

        List<Component> lore = List.of(
            Component.literal("§7Plays in any Jukebox"),
            Component.literal("§8File: " + fileName)
        );
        disc.set(DataComponents.LORE, new ItemLore(lore));

        return disc;
    }

    public static String cleanTitle(String filename) {
        int dot = filename.lastIndexOf('.');
        String base = (dot != -1) ? filename.substring(0, dot) : filename;
        return base.replace('_', ' ').replace('-', ' ');
    }
}
