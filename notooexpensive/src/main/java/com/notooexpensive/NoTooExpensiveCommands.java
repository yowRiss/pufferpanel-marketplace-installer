package com.notooexpensive;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class NoTooExpensiveCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = buildCommand("notooexpensive");
        LiteralArgumentBuilder<CommandSourceStack> alias = buildCommand("nte");

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
            .then(Commands.literal("status")
                .executes(ctx -> {
                    NoTooExpensiveConfig config = NoTooExpensiveConfig.get();
                    ctx.getSource().sendSuccess(() -> Component.literal("§6§m----------------------------------------"), false);
                    ctx.getSource().sendSuccess(() -> Component.literal("§6§l★ NoTooExpensive Status:"), false);
                    ctx.getSource().sendSuccess(() -> Component.literal("§7• Max Anvil Cost: §e" + config.maxAnvilCost + " levels" + (config.maxAnvilCost == 39 ? " §a(Vanilla Client Safe)" : "")), false);
                    ctx.getSource().sendSuccess(() -> Component.literal("§7• Max Prior Work Penalty: §e" + config.maxPriorWorkPenalty + " levels"), false);
                    ctx.getSource().sendSuccess(() -> Component.literal("§6§m----------------------------------------"), false);
                    return 1;
                })
            )
            .then(Commands.literal("setcost")
                .requires(NoTooExpensiveCommands::isOp)
                .then(Commands.argument("cost", IntegerArgumentType.integer(1, 39))
                    .executes(ctx -> {
                        int cost = IntegerArgumentType.getInteger(ctx, "cost");
                        NoTooExpensiveConfig config = NoTooExpensiveConfig.get();
                        config.maxAnvilCost = cost;
                        NoTooExpensiveConfig.save();
                        ctx.getSource().sendSuccess(() -> Component.literal("§a[NoTooExpensive] Max anvil cost set to §e" + cost + "§a levels!"), true);
                        return 1;
                    })
                )
            )
            .then(Commands.literal("setpenalty")
                .requires(NoTooExpensiveCommands::isOp)
                .then(Commands.argument("penalty", IntegerArgumentType.integer(0, 1000))
                    .executes(ctx -> {
                        int penalty = IntegerArgumentType.getInteger(ctx, "penalty");
                        NoTooExpensiveConfig config = NoTooExpensiveConfig.get();
                        config.maxPriorWorkPenalty = penalty;
                        NoTooExpensiveConfig.save();
                        ctx.getSource().sendSuccess(() -> Component.literal("§a[NoTooExpensive] Max prior work penalty set to §e" + penalty + "§a levels!"), true);
                        return 1;
                    })
                )
            )
            .then(Commands.literal("reload")
                .requires(NoTooExpensiveCommands::isOp)
                .executes(ctx -> {
                    NoTooExpensiveConfig.load();
                    ctx.getSource().sendSuccess(() -> Component.literal("§a[NoTooExpensive] Configuration reloaded from disk!"), true);
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
        source.sendSuccess(() -> Component.literal("§6§l★ NoTooExpensive Commands:"), false);
        source.sendSuccess(() -> Component.literal("§e/nte status §7- View current anvil cost & penalty settings"), false);
        source.sendSuccess(() -> Component.literal("§e/nte setcost <1-39> §7- [OP] Set maximum anvil enchantment cost"), false);
        source.sendSuccess(() -> Component.literal("§e/nte setpenalty <penalty> §7- [OP] Set maximum prior work penalty"), false);
        source.sendSuccess(() -> Component.literal("§e/nte reload §7- [OP] Reload config file"), false);
        source.sendSuccess(() -> Component.literal("§6§m----------------------------------------"), false);
    }
}
