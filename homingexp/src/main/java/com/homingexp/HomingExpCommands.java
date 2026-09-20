package com.homingexp;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class HomingExpCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = buildCommand("homingexp");
        LiteralArgumentBuilder<CommandSourceStack> alias = buildCommand("hexp");

        dispatcher.register(root);
        dispatcher.register(alias);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildCommand(String name) {
        return Commands.literal(name)
            .executes(ctx -> {
                sendHelp(ctx.getSource());
                return 1;
            })
            .then(Commands.literal("help")
                .executes(ctx -> {
                    sendHelp(ctx.getSource());
                    return 1;
                })
            )
            .then(Commands.literal("toggle")
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    HomingExpConfig config = HomingExpConfig.get();
                    boolean current = config.isPlayerEnabled(player.getUUID());
                    boolean next = !current;
                    config.setPlayerEnabled(player.getUUID(), next);
                    if (next) {
                        player.sendSystemMessage(Component.literal("§a[HomingExp] Homing experience §2§lENABLED§a! Mode: §e" + config.getPlayerMode(player.getUUID()).toUpperCase()));
                    } else {
                        player.sendSystemMessage(Component.literal("§c[HomingExp] Homing experience §4§lDISABLED§c."));
                    }
                    return 1;
                })
            )
            .then(Commands.literal("mode")
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    HomingExpConfig config = HomingExpConfig.get();
                    player.sendSystemMessage(Component.literal("§6[HomingExp] Current mode: §e" + config.getPlayerMode(player.getUUID()).toUpperCase() + "§7. Usage: §b/hexp mode <homing|direct|hybrid>"));
                    return 1;
                })
                .then(Commands.argument("type", StringArgumentType.word())
                    .suggests((ctx, builder) -> {
                        builder.suggest("homing");
                        builder.suggest("direct");
                        builder.suggest("hybrid");
                        return builder.buildFuture();
                    })
                    .executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                        String type = StringArgumentType.getString(ctx, "type").toLowerCase();
                        HomingExpConfig config = HomingExpConfig.get();

                        if (!type.equals("homing") && !type.equals("direct") && !type.equals("hybrid")) {
                            player.sendSystemMessage(Component.literal("§c[HomingExp] Invalid mode! Available modes: §ehoming§c, §edirect§c, §ehybrid"));
                            return 0;
                        }

                        config.setPlayerMode(player.getUUID(), type);
                        String desc = switch (type) {
                            case "direct" -> "Experience drops directly at your feet instantly from any distance!";
                            case "hybrid" -> "Experience drops directly if far away, or flies to you if nearby!";
                            default -> "Experience orbs fly through the air across the world directly to you!";
                        };
                        player.sendSystemMessage(Component.literal("§a[HomingExp] Mode set to §e§l" + type.toUpperCase() + "§a. " + desc));
                        return 1;
                    })
                )
            )
            .then(Commands.literal("status")
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    HomingExpConfig config = HomingExpConfig.get();
                    boolean enabled = config.isPlayerEnabled(player.getUUID());
                    String mode = config.getPlayerMode(player.getUUID());

                    player.sendSystemMessage(Component.literal("§6§m----------------------------------------"));
                    player.sendSystemMessage(Component.literal("§6§l★ HomingExp Status:"));
                    player.sendSystemMessage(Component.literal("§7• Status: " + (enabled ? "§a§lENABLED" : "§c§lDISABLED")));
                    player.sendSystemMessage(Component.literal("§7• Delivery Mode: §e" + mode.toUpperCase()));
                    player.sendSystemMessage(Component.literal("§7• Max Range: §b" + (int) config.maxRange + " blocks"));
                    player.sendSystemMessage(Component.literal("§7• Loose XP Magnet: " + (config.magnetLooseXp ? "§aActive (" + (int) config.looseXpRange + "m)" : "§cInactive")));
                    player.sendSystemMessage(Component.literal("§6§m----------------------------------------"));
                    return 1;
                })
            )
            .then(Commands.literal("range")
                .requires(HomingExpCommands::isOp)
                .then(Commands.argument("blocks", DoubleArgumentType.doubleArg(8.0, 256.0))
                    .executes(ctx -> {
                        double range = DoubleArgumentType.getDouble(ctx, "blocks");
                        HomingExpConfig config = HomingExpConfig.get();
                        config.maxRange = range;
                        HomingExpConfig.save();
                        ctx.getSource().sendSuccess(() -> Component.literal("§a[HomingExp] Maximum homing range set to §e" + range + "§a blocks!"), true);
                        return 1;
                    })
                )
            )
            .then(Commands.literal("reload")
                .requires(HomingExpCommands::isOp)
                .executes(ctx -> {
                    HomingExpConfig.load();
                    ctx.getSource().sendSuccess(() -> Component.literal("§a[HomingExp] Configuration reloaded from disk!"), true);
                    return 1;
                })
            );
    }

    public static boolean isOp(CommandSourceStack source) {
        if (source.getPlayer() == null) return true;
        return source.getServer().getPlayerList().isOp(source.getPlayer().nameAndId());
    }

    private static void sendHelp(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("§6§m----------------------------------------"), false);
        source.sendSuccess(() -> Component.literal("§6§l★ HomingExp Commands:"), false);
        source.sendSuccess(() -> Component.literal("§e/hexp toggle §7- Enable / disable homing exp for yourself"), false);
        source.sendSuccess(() -> Component.literal("§e/hexp mode <homing|direct|hybrid> §7- Select experience delivery style"), false);
        source.sendSuccess(() -> Component.literal("§e/hexp status §7- View your current settings"), false);
        source.sendSuccess(() -> Component.literal("§e/hexp range <blocks> §7- [OP] Set max homing distance"), false);
        source.sendSuccess(() -> Component.literal("§e/hexp reload §7- [OP] Reload config file"), false);
        source.sendSuccess(() -> Component.literal("§6§m----------------------------------------"), false);
    }
}
