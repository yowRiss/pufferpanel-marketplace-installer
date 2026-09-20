package com.smartdifficulty;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public class SmartDifficultyCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // /day command for everyone
        dispatcher.register(Commands.literal("day")
            .executes(ctx -> {
                sendDayInfo(ctx.getSource());
                return 1;
            })
        );
        dispatcher.register(Commands.literal("daycounter")
            .executes(ctx -> {
                sendDayInfo(ctx.getSource());
                return 1;
            })
        );

        // /sd and /smartdifficulty
        LiteralArgumentBuilder<CommandSourceStack> root = buildSdCommand("smartdifficulty");
        LiteralArgumentBuilder<CommandSourceStack> alias = buildSdCommand("sd");

        dispatcher.register(root);
        dispatcher.register(alias);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildSdCommand(String name) {
        return Commands.literal(name)
            .executes(ctx -> {
                sendDayInfo(ctx.getSource());
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
                    sendDayInfo(ctx.getSource());
                    return 1;
                })
            )
            .then(Commands.literal("setday")
                .requires(SmartDifficultyCommands::isOp)
                .then(Commands.argument("day", LongArgumentType.longArg(1, 100000))
                    .executes(ctx -> {
                        long targetDay = LongArgumentType.getLong(ctx, "day");
                        ServerLevel level = ctx.getSource().getLevel();
                        long rawDay = SmartDifficultyManager.getWorldTime(level) / 24000L;
                        SmartDifficultyConfig config = SmartDifficultyConfig.get();
                        config.dayOffset = targetDay - rawDay - 1L;
                        SmartDifficultyConfig.save();

                        long effectiveDay = SmartDifficultyManager.getDay(level);
                        int tier = SmartDifficultyManager.getDifficultyTier(effectiveDay, SmartDifficultyManager.isFullMoon(level));

                        ctx.getSource().sendSuccess(() -> Component.literal("§a[SmartDifficulty] World day set to §eDay " + effectiveDay + " §7(" + SmartDifficultyManager.getTierDescription(tier) + ")!"), true);
                        return 1;
                    })
                )
            )
            .then(Commands.literal("resetday")
                .requires(SmartDifficultyCommands::isOp)
                .executes(ctx -> {
                    SmartDifficultyConfig config = SmartDifficultyConfig.get();
                    config.dayOffset = 0;
                    SmartDifficultyConfig.save();
                    ServerLevel level = ctx.getSource().getLevel();
                    long currentDay = SmartDifficultyManager.getDay(level);
                    ctx.getSource().sendSuccess(() -> Component.literal("§a[SmartDifficulty] Day offset reset to natural game time (Current: Day " + currentDay + ")."), true);
                    return 1;
                })
            )
            .then(Commands.literal("toggle")
                .requires(SmartDifficultyCommands::isOp)
                .executes(ctx -> {
                    SmartDifficultyConfig config = SmartDifficultyConfig.get();
                    config.enabled = !config.enabled;
                    SmartDifficultyConfig.save();
                    ctx.getSource().sendSuccess(() -> Component.literal("§a[SmartDifficulty] Smart difficulty scaling is now " + (config.enabled ? "§2§lENABLED" : "§c§lDISABLED") + "§a."), true);
                    return 1;
                })
            )
            .then(Commands.literal("reload")
                .requires(SmartDifficultyCommands::isOp)
                .executes(ctx -> {
                    SmartDifficultyConfig.load();
                    ctx.getSource().sendSuccess(() -> Component.literal("§a[SmartDifficulty] Configuration reloaded from disk!"), true);
                    return 1;
                })
            );
    }

    private static void sendDayInfo(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        long day = SmartDifficultyManager.getDay(level);
        long worldTime = SmartDifficultyManager.getWorldTime(level);
        long timeOfDay = worldTime % 24000L;
        boolean night = SmartDifficultyManager.isNight(level);
        boolean fullMoon = SmartDifficultyManager.isFullMoon(level);
        String moonPhase = SmartDifficultyManager.getMoonPhaseName(level);
        int tier = SmartDifficultyManager.getDifficultyTier(day, fullMoon);

        int hours = (int) ((timeOfDay / 1000 + 6) % 24);
        int minutes = (int) ((timeOfDay % 1000) * 60 / 1000);
        String timeStr = String.format("%02d:%02d (%s)", hours, minutes, night ? "§9Night" : "§eDay");

        source.sendSuccess(() -> Component.literal("§6§m----------------------------------------"), false);
        source.sendSuccess(() -> Component.literal("§6§l★ World Day & Difficulty Status:"), false);
        source.sendSuccess(() -> Component.literal("§e• Day: §f§lDay " + day), false);
        source.sendSuccess(() -> Component.literal("§e• Time: §f" + timeStr), false);
        source.sendSuccess(() -> Component.literal("§e• Moon Phase: §b" + moonPhase + (fullMoon ? " §c§l[NO SLEEP TONIGHT!]" : "")), false);
        source.sendSuccess(() -> Component.literal("§e• Difficulty Tier: §a" + SmartDifficultyManager.getTierDescription(tier)), false);
        source.sendSuccess(() -> Component.literal("§e• Mob Scaling: " + (SmartDifficultyConfig.get().enabled ? "§2Active" : "§cDisabled")), false);
        source.sendSuccess(() -> Component.literal("§6§m----------------------------------------"), false);
    }

    private static void sendHelp(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("§6§m----------------------------------------"), false);
        source.sendSuccess(() -> Component.literal("§6§l★ SmartDifficulty Commands:"), false);
        source.sendSuccess(() -> Component.literal("§e/day §7- Check current world day & moon phase"), false);
        source.sendSuccess(() -> Component.literal("§e/sd status §7- View world difficulty tier and mob stats"), false);
        source.sendSuccess(() -> Component.literal("§e/sd setday <number> §7- [OP] Set world day offset for difficulty"), false);
        source.sendSuccess(() -> Component.literal("§e/sd resetday §7- [OP] Reset day back to natural world time"), false);
        source.sendSuccess(() -> Component.literal("§e/sd toggle §7- [OP] Enable or disable mob difficulty scaling"), false);
        source.sendSuccess(() -> Component.literal("§e/sd reload §7- [OP] Reload config from disk"), false);
        source.sendSuccess(() -> Component.literal("§6§m----------------------------------------"), false);
    }

    public static boolean isOp(CommandSourceStack source) {
        if (source.getPlayer() == null) return true;
        return source.getServer().getPlayerList().isOp(source.getPlayer().nameAndId());
    }
}
