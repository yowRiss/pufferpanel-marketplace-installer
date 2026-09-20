package com.autoclicker;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class AutoClickCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = buildCommandTree("autoclick");
        LiteralArgumentBuilder<CommandSourceStack> alias = buildCommandTree("ac");

        dispatcher.register(root);
        dispatcher.register(alias);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildCommandTree(String name) {
        return Commands.literal(name)
            .then(Commands.literal("attack")
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    return toggleMode(player, AutoClickSession.Mode.ATTACK, 0);
                })
                .then(Commands.argument("interval", IntegerArgumentType.integer(2, 100))
                    .executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                        int interval = IntegerArgumentType.getInteger(ctx, "interval");
                        return startMode(player, AutoClickSession.Mode.ATTACK, interval);
                    })
                )
            )
            .then(Commands.literal("mine")
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    return toggleMode(player, AutoClickSession.Mode.MINE, 0);
                })
            )
            .then(Commands.literal("break")
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    return toggleMode(player, AutoClickSession.Mode.MINE, 0);
                })
            )
            .then(Commands.literal("stop")
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    return stopAutoClick(player);
                })
            )
            .then(Commands.literal("off")
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    return stopAutoClick(player);
                })
            )
            .then(Commands.literal("status")
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    return showStatus(player);
                })
            )
            .then(Commands.literal("toolprotection")
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    return toggleToolProtection(player);
                })
            )
            .then(Commands.literal("pvp")
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    return togglePvp(player);
                })
            )
            .then(Commands.literal("popup")
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    AutoClickWelcomeManager.showPopup(player);
                    return 1;
                })
            )
            .then(Commands.literal("welcome")
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    AutoClickWelcomeManager.showPopup(player);
                    return 1;
                })
            )
            .then(Commands.literal("help")
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    return showHelp(player);
                })
            )
            .executes(ctx -> {
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                return showHelp(player);
            });
    }

    private static int toggleMode(ServerPlayer player, AutoClickSession.Mode mode, int interval) {
        AutoClickSession session = AutoClickManager.getSession(player.getUUID());
        if (session != null && session.getMode() == mode) {
            AutoClickManager.stop(player);
            return 1;
        }

        return startMode(player, mode, interval);
    }

    private static int startMode(ServerPlayer player, AutoClickSession.Mode mode, int interval) {
        AutoClickSession session = AutoClickManager.start(player, mode, interval);
        if (mode == AutoClickSession.Mode.ATTACK) {
            String speedStr = (interval > 0) ? (interval + " ticks") : "Auto Weapon Cooldown (Sweeping Edge)";
            player.sendSystemMessage(Component.literal("§a[AutoClicker] Mode: §e⚔ ATTACK §aaktif!"));
            player.sendSystemMessage(Component.literal("§7Speed: §f" + speedStr + " §7| Ketik §e/ac off §7untuk berhenti."));
        } else {
            player.sendSystemMessage(Component.literal("§a[AutoClicker] Mode: §e⛏ MINE / BREAK §aaktif!"));
            player.sendSystemMessage(Component.literal("§7Arahkan pandangan ke blok yang ingin ditambang | Ketik §e/ac off §7untuk berhenti."));
        }
        return 1;
    }

    private static int stopAutoClick(ServerPlayer player) {
        if (AutoClickManager.stop(player)) {
            return 1;
        } else {
            player.sendSystemMessage(Component.literal("§7[AutoClicker] Anda sedang tidak mengaktifkan Auto Clicker."));
            return 0;
        }
    }

    private static int showStatus(ServerPlayer player) {
        AutoClickSession session = AutoClickManager.getSession(player.getUUID());
        if (session == null) {
            player.sendSystemMessage(Component.literal("§7[AutoClicker] Status: §cNONAKTIF"));
            player.sendSystemMessage(Component.literal("§7Gunakan: §a/ac attack §7atau §a/ac mine"));
        } else {
            String modeStr = (session.getMode() == AutoClickSession.Mode.ATTACK) ? "§e⚔ ATTACK" : "§e⛏ MINE";
            player.sendSystemMessage(Component.literal("§a=== [AutoClicker Status] ==="));
            player.sendSystemMessage(Component.literal("§7Mode: " + modeStr));
            player.sendSystemMessage(Component.literal("§7Tool Protection: " + (session.isToolProtection() ? "§aON" : "§cOFF")));
            player.sendSystemMessage(Component.literal("§7PvP Targeting: " + (session.isPvp() ? "§aON" : "§cOFF (Hanya Mob)")));
            player.sendSystemMessage(Component.literal("§7Ketik §c/ac off §7untuk menonaktifkan."));
        }
        return 1;
    }

    private static int toggleToolProtection(ServerPlayer player) {
        AutoClickSession session = AutoClickManager.getSession(player.getUUID());
        if (session == null) {
            player.sendSystemMessage(Component.literal("§7Aktifkan Auto Clicker terlebih dahulu: §a/ac attack §7atau §a/ac mine"));
            return 0;
        }
        session.setToolProtection(!session.isToolProtection());
        player.sendSystemMessage(Component.literal("§6[AutoClicker] Tool Protection: " + (session.isToolProtection() ? "§aON (Otomatis stop sebelum tool hancur)" : "§cOFF")));
        return 1;
    }

    private static int togglePvp(ServerPlayer player) {
        AutoClickSession session = AutoClickManager.getSession(player.getUUID());
        if (session == null) {
            player.sendSystemMessage(Component.literal("§7Aktifkan Auto Clicker terlebih dahulu: §a/ac attack §7atau §a/ac mine"));
            return 0;
        }
        session.setPvp(!session.isPvp());
        player.sendSystemMessage(Component.literal("§6[AutoClicker] Target PvP: " + (session.isPvp() ? "§cON (Dapat memukul player)" : "§aOFF (Hanya memukul mob)")));
        return 1;
    }

    private static int showHelp(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("§6=== Auto Clicker Commands ==="));
        player.sendSystemMessage(Component.literal("§a/ac attack §7- Auto swing sword / pukul mob (full power & sweep)"));
        player.sendSystemMessage(Component.literal("§a/ac attack <ticks> §7- Auto attack dengan kecepatan kustom (contoh: /ac attack 10)"));
        player.sendSystemMessage(Component.literal("§a/ac mine §7- Auto hancurkan blok di hadapan crosshair"));
        player.sendSystemMessage(Component.literal("§a/ac off §7- Hentikan auto clicker"));
        player.sendSystemMessage(Component.literal("§a/ac status §7- Lihat status saat ini"));
        player.sendSystemMessage(Component.literal("§a/ac toolprotection §7- Toggle perlindungan tool agar tidak hancur"));
        player.sendSystemMessage(Component.literal("§a/ac pvp §7- Toggle target player atau mob saja"));
        return 1;
    }
}
