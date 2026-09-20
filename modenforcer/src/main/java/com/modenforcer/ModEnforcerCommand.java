package com.modenforcer;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;

public class ModEnforcerCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("modenforcer")
                .requires(ModEnforcerCommand::isAuthorized)
                .executes(ModEnforcerCommand::openGui)
                .then(Commands.literal("gui").executes(ModEnforcerCommand::openGui))
                .then(Commands.literal("toggle").executes(ModEnforcerCommand::toggleMaster))
                .then(Commands.literal("list").executes(ModEnforcerCommand::listMods))
                .then(Commands.literal("reload").executes(ModEnforcerCommand::reloadConfig))
                .then(Commands.literal("backup").executes(ModEnforcerCommand::triggerBackup))
                .then(Commands.literal("tabping")
                        .executes(ModEnforcerCommand::toggleTabPing)
                        .then(Commands.literal("enable").executes(ctx -> setTabPingState(ctx, true)))
                        .then(Commands.literal("disable").executes(ctx -> setTabPingState(ctx, false)))
                        .then(Commands.literal("toggle").executes(ModEnforcerCommand::toggleTabPing)))
                .then(Commands.literal("enable")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(ctx -> setModState(ctx, true))))
                .then(Commands.literal("disable")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(ctx -> setModState(ctx, false))));

        dispatcher.register(root);

        // Alias /modcheck
        LiteralArgumentBuilder<CommandSourceStack> alias = Commands.literal("modcheck")
                .requires(ModEnforcerCommand::isAuthorized)
                .executes(ModEnforcerCommand::openGui)
                .then(Commands.literal("gui").executes(ModEnforcerCommand::openGui))
                .then(Commands.literal("toggle").executes(ModEnforcerCommand::toggleMaster))
                .then(Commands.literal("list").executes(ModEnforcerCommand::listMods))
                .then(Commands.literal("reload").executes(ModEnforcerCommand::reloadConfig))
                .then(Commands.literal("backup").executes(ModEnforcerCommand::triggerBackup))
                .then(Commands.literal("tabping")
                        .executes(ModEnforcerCommand::toggleTabPing)
                        .then(Commands.literal("enable").executes(ctx -> setTabPingState(ctx, true)))
                        .then(Commands.literal("disable").executes(ctx -> setTabPingState(ctx, false)))
                        .then(Commands.literal("toggle").executes(ModEnforcerCommand::toggleTabPing)))
                .then(Commands.literal("enable")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(ctx -> setModState(ctx, true))))
                .then(Commands.literal("disable")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(ctx -> setModState(ctx, false))));

        dispatcher.register(alias);

        // Player /ping command (accessible to all players)
        dispatcher.register(Commands.literal("ping")
                .executes(ModEnforcerCommand::showSelfPing)
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(ModEnforcerCommand::showTargetPing)));

        // Dedicated /backup command (accessible to OPs and console)
        dispatcher.register(Commands.literal("backup")
                .requires(ModEnforcerCommand::isAuthorized)
                .executes(ModEnforcerCommand::triggerBackup));
    }

    private static boolean isAuthorized(CommandSourceStack source) {
        if (!source.isPlayer()) {
            return true; // Console always allowed
        }
        try {
            ServerPlayer player = source.getPlayer();
            if (player == null) return false;
            return source.permissions().hasPermission(Permissions.COMMANDS_ADMIN)
                    || source.getServer().getPlayerList().isOp(player.nameAndId());
        } catch (Exception e) {
            return false;
        }
    }

    private static int openGui(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!source.isPlayer()) {
            source.sendFailure(Component.literal("§cThe in-game GUI can only be opened by a player. Use console subcommands instead."));
            return 0;
        }

        try {
            ServerPlayer player = source.getPlayerOrException();
            SimpleContainer container = ModEnforcerMenu.createContainer();
            player.openMenu(new SimpleMenuProvider(
                    (syncId, inv, p) -> new ModEnforcerMenu(syncId, inv, container, ModEnforcerConfig.get()),
                    Component.literal("§8Mod Enforcer - OP Control")
            ));
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("§cFailed to open GUI: " + e.getMessage()));
            return 0;
        }
    }

    private static int toggleMaster(CommandContext<CommandSourceStack> ctx) {
        ModEnforcerConfig config = ModEnforcerConfig.get();
        boolean active = config.toggleMaster();
        ctx.getSource().sendSuccess(() -> Component.literal("§6[ModEnforcer] §fMaster enforcement is now " + (active ? "§a§lENABLED" : "§c§lDISABLED")), true);
        return 1;
    }

    private static int listMods(CommandContext<CommandSourceStack> ctx) {
        ModEnforcerConfig config = ModEnforcerConfig.get();
        CommandSourceStack source = ctx.getSource();
        source.sendSuccess(() -> Component.literal("§6=== Mod Enforcer Status ==="), false);
        source.sendSuccess(() -> Component.literal("§7Master Switch: " + (config.enabled ? "§aENABLED" : "§cDISABLED")), false);
        source.sendSuccess(() -> Component.literal("§7Configured Mods:"), false);
        for (ModEnforcerConfig.RequiredMod mod : config.requiredMods) {
            String status = mod.enabled ? "§a[REQUIRED]" : "§7[OPTIONAL]";
            source.sendSuccess(() -> Component.literal("  " + status + " §e" + mod.name + " §8(id: §f" + mod.id + "§8, channel: §f" + mod.channel + "§8)"), false);
        }
        return 1;
    }

    private static int setModState(CommandContext<CommandSourceStack> ctx, boolean targetState) {
        String id = StringArgumentType.getString(ctx, "id");
        ModEnforcerConfig config = ModEnforcerConfig.get();
        ModEnforcerConfig.RequiredMod mod = config.getMod(id);
        if (mod == null) {
            ctx.getSource().sendFailure(Component.literal("§cMod with ID '" + id + "' not found. Use /modenforcer list to see available IDs."));
            return 0;
        }
        mod.enabled = targetState;
        config.save();
        ctx.getSource().sendSuccess(() -> Component.literal("§6[ModEnforcer] §e" + mod.name + " §fis now " + (targetState ? "§a§lREQUIRED" : "§7§lOPTIONAL")), true);
        return 1;
    }

    private static int reloadConfig(CommandContext<CommandSourceStack> ctx) {
        ModEnforcerConfig.reload();
        ctx.getSource().sendSuccess(() -> Component.literal("§a[ModEnforcer] Configuration reloaded successfully!"), true);
        return 1;
    }

    private static int showSelfPing(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!source.isPlayer()) {
            source.sendSuccess(() -> Component.literal("§6[Ping] §fConsole latency: §a0ms"), false);
            return 1;
        }
        try {
            ServerPlayer player = source.getPlayerOrException();
            int rawPing = (player.connection != null) ? player.connection.latency() : 0;
            int ping = Math.max(0, rawPing);
            ChatFormatting color = TabPingManager.getPingColor(ping);
            final String pingStr = ping + "ms";
            source.sendSuccess(() -> Component.literal("§6[Ping] §fYour latency is: ")
                    .append(Component.literal(pingStr).withStyle(color)), false);
            return 1;
        } catch (Exception e) {
            return 0;
        }
    }

    private static int showTargetPing(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
            int rawPing = (target.connection != null) ? target.connection.latency() : 0;
            int ping = Math.max(0, rawPing);
            ChatFormatting color = TabPingManager.getPingColor(ping);
            final String targetName = target.getGameProfile().name();
            final String pingStr = ping + "ms";
            ctx.getSource().sendSuccess(() -> Component.literal("§6[Ping] §e" + targetName + "§f's latency is: ")
                    .append(Component.literal(pingStr).withStyle(color)), false);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("§cPlayer not found."));
            return 0;
        }
    }

    private static int toggleTabPing(CommandContext<CommandSourceStack> ctx) {
        ModEnforcerConfig config = ModEnforcerConfig.get();
        boolean active = config.toggleTabPing();
        if (active) {
            TabPingManager.broadcastUpdateAll(ctx.getSource().getServer());
        } else {
            TabPingManager.broadcastResetAll(ctx.getSource().getServer());
        }
        ctx.getSource().sendSuccess(() -> Component.literal("§6[ModEnforcer] §fTab Ping display is now " + (active ? "§a§lENABLED" : "§c§lDISABLED")), true);
        return 1;
    }

    private static int setTabPingState(CommandContext<CommandSourceStack> ctx, boolean targetState) {
        ModEnforcerConfig config = ModEnforcerConfig.get();
        config.tabPingEnabled = targetState;
        config.save();
        if (targetState) {
            TabPingManager.broadcastUpdateAll(ctx.getSource().getServer());
        } else {
            TabPingManager.broadcastResetAll(ctx.getSource().getServer());
        }
        ctx.getSource().sendSuccess(() -> Component.literal("§6[ModEnforcer] §fTab Ping display is now " + (targetState ? "§a§lENABLED" : "§c§lDISABLED")), true);
        return 1;
    }

    private static int triggerBackup(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        source.sendSuccess(() -> Component.literal("§6[Backup] §fStarting automated server backup..."), true);
        Thread t = new Thread(() -> {
            try {
                Process proc = new ProcessBuilder("/usr/local/bin/pufferpanel-auto-backup.py").start();
                int exitCode = proc.waitFor();
                if (exitCode == 0) {
                    source.sendSuccess(() -> Component.literal("§a[Backup] Server backup completed successfully and registered in PufferPanel! (Max 3 retained)"), true);
                } else {
                    source.sendFailure(Component.literal("§c[Backup] Server backup failed with exit code " + exitCode));
                }
            } catch (Exception e) {
                source.sendFailure(Component.literal("§c[Backup] Failed to trigger backup: " + e.getMessage()));
            }
        }, "BackupTriggerThread");
        t.setDaemon(true);
        t.start();
        return 1;
    }
}
