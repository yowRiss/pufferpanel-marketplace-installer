package com.smartdifficulty;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundTabListPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import java.util.concurrent.ThreadLocalRandom;

public class SmartDifficultyManager {

    private static long lastKnownDay = -1;
    private static boolean fullMoonAlertSent = false;
    private static long lastTabUpdate = -1; // tracks tick of last tab list update

    public static long getWorldTime(Level level) {
        try {
            return level.getOverworldClockTime();
        } catch (Throwable t) {
            return level.getLevelData().getGameTime();
        }
    }

    public static long getDay(Level level) {
        long rawDay = getWorldTime(level) / 24000L;
        long day = rawDay + SmartDifficultyConfig.get().dayOffset + 1L;
        return Math.max(1L, day);
    }

    public static int getMoonPhaseIndex(Level level) {
        long rawDay = getWorldTime(level) / 24000L;
        return (int) (rawDay % 8L);
    }

    public static String getMoonPhaseName(Level level) {
        int index = getMoonPhaseIndex(level);
        return switch (index) {
            case 0 -> "Full Moon";
            case 1 -> "Waning Gibbous";
            case 2 -> "Third Quarter";
            case 3 -> "Waning Crescent";
            case 4 -> "New Moon";
            case 5 -> "Waxing Crescent";
            case 6 -> "First Quarter";
            case 7 -> "Waxing Gibbous";
            default -> "Unknown";
        };
    }

    public static boolean isFullMoon(Level level) {
        return getMoonPhaseIndex(level) == 0;
    }

    public static boolean isNight(Level level) {
        long timeOfDay = getWorldTime(level) % 24000L;
        return timeOfDay >= 12500L && timeOfDay <= 23500L;
    }

    public static int getDifficultyTier(long day, boolean fullMoon) {
        int tier;
        if (day < 25) {
            tier = 1;
        } else if (day < 50) {
            tier = 2;
        } else if (day < 100) {
            tier = 3;
        } else if (day < 200) {
            tier = 4; // Day 100+: Iron Era
        } else if (day < 300) {
            tier = 5; // Day 200+: Diamond Era (randomized slots)
        } else {
            tier = 6; // Day 300+: Endgame Era
        }

        if (fullMoon) {
            tier = Math.min(6, tier + 1);
        }
        return tier;
    }

    public static String getTierDescription(int tier) {
        return switch (tier) {
            case 1 -> "Tier 1: Early Survival (Days 1–24)";
            case 2 -> "Tier 2: Developing World (Days 25–49)";
            case 3 -> "Tier 3: Hardened Hostiles (Days 50–99)";
            case 4 -> "Tier 4: Iron Armor Era (Days 100–199)";
            case 5 -> "Tier 5: Diamond Armor Era (Days 200–299)";
            default -> "Tier 6: Nightmare Era (Days 300+)";
        };
    }

    public static Player.BedSleepingProblem checkSleep(Player player, BlockPos pos) {
        SmartDifficultyConfig config = SmartDifficultyConfig.get();
        if (!config.enabled || !config.preventSleepOnFullMoon) {
            return null;
        }

        Level level = player.level();
        if (isFullMoon(level) && isNight(level)) {
            player.playSound(SoundEvents.BELL_BLOCK, 0.8f, 0.8f);
            return new Player.BedSleepingProblem(Component.literal("§cYou cannot sleep during the Full Moon! The monsters are restless tonight..."));
        }
        return null;
    }

    public static void scaleMob(Mob mob, ServerLevel world) {
        SmartDifficultyConfig config = SmartDifficultyConfig.get();
        if (!config.enabled) return;

        if (mob.entityTags().contains("sd_scaled")) return;
        mob.addTag("sd_scaled");

        if (!(mob instanceof Monster)) return;

        long day = getDay(world);
        boolean fullMoon = isFullMoon(world);
        int tier = getDifficultyTier(day, fullMoon);
        ThreadLocalRandom rnd = ThreadLocalRandom.current();

        // 1. Attribute buffs
        if (config.buffMobAttributes) {
            applyAttributeBuffs(mob, tier, fullMoon, rnd);
        }

        // 2. Equipment scaling
        if (mob instanceof Zombie || mob instanceof AbstractSkeleton) {
            applyEquipmentScaling(mob, tier, day, fullMoon, rnd, config.mobEquipmentDropChance);
        }

        // 3. Full moon potion effects
        if (config.buffMobEffects && fullMoon && isNight(world)) {
            applyFullMoonEffects(mob, rnd);
        }
    }

    private static void applyAttributeBuffs(Mob mob, int tier, boolean fullMoon, ThreadLocalRandom rnd) {
        // Health multiplier
        double healthBonus = switch (tier) {
            case 2 -> 1.10;
            case 3 -> 1.25;
            case 4 -> 1.40; // Day 100: +40% health
            case 5 -> 1.65; // Day 200: +65% health
            case 6 -> 2.00; // Day 300: +100% health
            default -> 1.0;
        };
        if (fullMoon) healthBonus += 0.20;

        AttributeInstance hpAttr = mob.getAttribute(Attributes.MAX_HEALTH);
        if (hpAttr != null && healthBonus > 1.0) {
            double newHp = hpAttr.getBaseValue() * healthBonus;
            hpAttr.setBaseValue(newHp);
            mob.setHealth((float) newHp);
        }

        // Speed multiplier
        double speedBonus = switch (tier) {
            case 2 -> 1.05;
            case 3 -> 1.10;
            case 4 -> 1.15;
            case 5 -> 1.20;
            case 6 -> 1.25;
            default -> 1.0;
        };
        if (fullMoon) speedBonus += 0.05;

        AttributeInstance speedAttr = mob.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr != null && speedBonus > 1.0) {
            speedAttr.setBaseValue(speedAttr.getBaseValue() * speedBonus);
        }

        // Attack damage
        double damageBonus = switch (tier) {
            case 3 -> 1.15;
            case 4 -> 1.25;
            case 5 -> 1.40;
            case 6 -> 1.60;
            default -> 1.0;
        };
        AttributeInstance dmgAttr = mob.getAttribute(Attributes.ATTACK_DAMAGE);
        if (dmgAttr != null && damageBonus > 1.0) {
            dmgAttr.setBaseValue(dmgAttr.getBaseValue() * damageBonus);
        }
    }

    private static void applyEquipmentScaling(Mob mob, int tier, long day, boolean fullMoon, ThreadLocalRandom rnd, float dropChance) {
        switch (tier) {
            case 1 -> {
                // Day 1-24: Rare leather/gold piece (15%)
                if (rnd.nextDouble() < 0.15) {
                    equipRandomPiece(mob, rnd.nextBoolean() ? Items.LEATHER_HELMET : Items.GOLDEN_HELMET, EquipmentSlot.HEAD, dropChance);
                }
            }
            case 2 -> {
                // Day 25-49: 35% chance for armor pieces (Leather, Gold, Chainmail, Copper)
                if (rnd.nextDouble() < 0.35) {
                    rollArmorSlot(mob, EquipmentSlot.HEAD, new Item[]{Items.LEATHER_HELMET, Items.GOLDEN_HELMET, Items.CHAINMAIL_HELMET, Items.COPPER_HELMET}, dropChance, rnd);
                    rollArmorSlot(mob, EquipmentSlot.FEET, new Item[]{Items.LEATHER_BOOTS, Items.GOLDEN_BOOTS, Items.CHAINMAIL_BOOTS, Items.COPPER_BOOTS}, dropChance, rnd);
                }
                if (rnd.nextDouble() < 0.25 && mob instanceof Zombie) {
                    equipWeapon(mob, rnd.nextBoolean() ? Items.STONE_SWORD : Items.COPPER_SWORD, dropChance);
                }
            }
            case 3 -> {
                // Day 50-99: 60% chance for armor (Copper, Chainmail, occasional Iron)
                if (rnd.nextDouble() < 0.60) {
                    rollArmorSlot(mob, EquipmentSlot.HEAD, new Item[]{Items.COPPER_HELMET, Items.CHAINMAIL_HELMET, Items.IRON_HELMET}, dropChance, rnd);
                    rollArmorSlot(mob, EquipmentSlot.CHEST, new Item[]{Items.COPPER_CHESTPLATE, Items.CHAINMAIL_CHESTPLATE, Items.IRON_CHESTPLATE}, dropChance, rnd);
                    rollArmorSlot(mob, EquipmentSlot.LEGS, new Item[]{Items.COPPER_LEGGINGS, Items.CHAINMAIL_LEGGINGS, Items.IRON_LEGGINGS}, dropChance, rnd);
                    rollArmorSlot(mob, EquipmentSlot.FEET, new Item[]{Items.COPPER_BOOTS, Items.CHAINMAIL_BOOTS, Items.IRON_BOOTS}, dropChance, rnd);
                }
                if (rnd.nextDouble() < 0.45 && mob instanceof Zombie) {
                    equipWeapon(mob, rnd.nextBoolean() ? Items.COPPER_SWORD : Items.IRON_SWORD, dropChance);
                }
            }
            case 4 -> {
                // Day 100+: User explicit requirement: "100day zombie biasa pakai armor iron"
                // 85% chance for Iron Armor!
                if (rnd.nextDouble() < 0.85) {
                    // Randomized slots with high iron bias
                    rollSlotWithBias(mob, EquipmentSlot.HEAD, Items.IRON_HELMET, Items.COPPER_HELMET, Items.CHAINMAIL_HELMET, 0.75, dropChance, rnd);
                    rollSlotWithBias(mob, EquipmentSlot.CHEST, Items.IRON_CHESTPLATE, Items.COPPER_CHESTPLATE, Items.CHAINMAIL_CHESTPLATE, 0.80, dropChance, rnd);
                    rollSlotWithBias(mob, EquipmentSlot.LEGS, Items.IRON_LEGGINGS, Items.COPPER_LEGGINGS, Items.CHAINMAIL_LEGGINGS, 0.75, dropChance, rnd);
                    rollSlotWithBias(mob, EquipmentSlot.FEET, Items.IRON_BOOTS, Items.COPPER_BOOTS, Items.CHAINMAIL_BOOTS, 0.75, dropChance, rnd);
                }
                if (mob instanceof Zombie && rnd.nextDouble() < 0.70) {
                    Item[] weapons = {Items.IRON_SWORD, Items.IRON_AXE, Items.COPPER_SWORD, Items.COPPER_AXE};
                    equipWeapon(mob, weapons[rnd.nextInt(weapons.length)], dropChance);
                }
            }
            case 5 -> {
                // Day 200+: User explicit requirement:
                // "jika 200day maka zombie pakai armor diamon tapi random bisa full armor atau cuma 1 armor
                // pokoknya random apa yg di pakai bisa full diamon tapi copper sword"
                if (rnd.nextDouble() < 0.95) {
                    // Each slot independently rolled!
                    // Can result in full diamond, 3 diamond, 2 diamond, or 1 diamond armor piece!
                    rollDay200Slot(mob, EquipmentSlot.HEAD, Items.DIAMOND_HELMET, Items.IRON_HELMET, Items.COPPER_HELMET, dropChance, rnd);
                    rollDay200Slot(mob, EquipmentSlot.CHEST, Items.DIAMOND_CHESTPLATE, Items.IRON_CHESTPLATE, Items.COPPER_CHESTPLATE, dropChance, rnd);
                    rollDay200Slot(mob, EquipmentSlot.LEGS, Items.DIAMOND_LEGGINGS, Items.IRON_LEGGINGS, Items.COPPER_LEGGINGS, dropChance, rnd);
                    rollDay200Slot(mob, EquipmentSlot.FEET, Items.DIAMOND_BOOTS, Items.IRON_BOOTS, Items.COPPER_BOOTS, dropChance, rnd);
                }

                if (mob instanceof Zombie && rnd.nextDouble() < 0.85) {
                    // Randomized weapons: Diamond sword, Iron sword, or Copper sword!
                    double wRoll = rnd.nextDouble();
                    Item weapon;
                    if (wRoll < 0.35) {
                        weapon = Items.DIAMOND_SWORD;
                    } else if (wRoll < 0.65) {
                        weapon = Items.COPPER_SWORD; // User explicit: "bisa full diamon tapi copper sword"
                    } else if (wRoll < 0.85) {
                        weapon = Items.IRON_SWORD;
                    } else {
                        weapon = rnd.nextBoolean() ? Items.DIAMOND_AXE : Items.COPPER_SPEAR;
                    }
                    equipWeapon(mob, weapon, dropChance);
                }
            }
            default -> {
                // Tier 6: Day 300+ Endgame
                // Heavy Diamond and Netherite gear
                rollEndgameSlot(mob, EquipmentSlot.HEAD, Items.NETHERITE_HELMET, Items.DIAMOND_HELMET, Items.IRON_HELMET, dropChance, rnd);
                rollEndgameSlot(mob, EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE, Items.DIAMOND_CHESTPLATE, Items.IRON_CHESTPLATE, dropChance, rnd);
                rollEndgameSlot(mob, EquipmentSlot.LEGS, Items.NETHERITE_LEGGINGS, Items.DIAMOND_LEGGINGS, Items.IRON_LEGGINGS, dropChance, rnd);
                rollEndgameSlot(mob, EquipmentSlot.FEET, Items.NETHERITE_BOOTS, Items.DIAMOND_BOOTS, Items.IRON_BOOTS, dropChance, rnd);

                if (mob instanceof Zombie) {
                    double w = rnd.nextDouble();
                    Item weapon = w < 0.25 ? Items.NETHERITE_SWORD : (w < 0.60 ? Items.DIAMOND_SWORD : Items.COPPER_SWORD);
                    equipWeapon(mob, weapon, dropChance);
                }
            }
        }
    }

    private static void rollDay200Slot(Mob mob, EquipmentSlot slot, Item diamondItem, Item ironItem, Item copperItem, float dropChance, ThreadLocalRandom rnd) {
        double r = rnd.nextDouble();
        // 42% Diamond, 43% Iron, 10% Copper, 5% None
        if (r < 0.42) {
            equipRandomPiece(mob, diamondItem, slot, dropChance);
        } else if (r < 0.85) {
            equipRandomPiece(mob, ironItem, slot, dropChance);
        } else if (r < 0.95) {
            equipRandomPiece(mob, copperItem, slot, dropChance);
        }
    }

    private static void rollEndgameSlot(Mob mob, EquipmentSlot slot, Item netheriteItem, Item diamondItem, Item ironItem, float dropChance, ThreadLocalRandom rnd) {
        double r = rnd.nextDouble();
        if (r < 0.20) {
            equipRandomPiece(mob, netheriteItem, slot, dropChance);
        } else if (r < 0.75) {
            equipRandomPiece(mob, diamondItem, slot, dropChance);
        } else {
            equipRandomPiece(mob, ironItem, slot, dropChance);
        }
    }

    private static void rollSlotWithBias(Mob mob, EquipmentSlot slot, Item mainItem, Item secItem, Item thirdItem, double mainProb, float dropChance, ThreadLocalRandom rnd) {
        double r = rnd.nextDouble();
        if (r < mainProb) {
            equipRandomPiece(mob, mainItem, slot, dropChance);
        } else if (r < mainProb + 0.15) {
            equipRandomPiece(mob, secItem, slot, dropChance);
        } else {
            equipRandomPiece(mob, thirdItem, slot, dropChance);
        }
    }

    private static void rollArmorSlot(Mob mob, EquipmentSlot slot, Item[] pool, float dropChance, ThreadLocalRandom rnd) {
        if (rnd.nextDouble() < 0.70) {
            Item chosen = pool[rnd.nextInt(pool.length)];
            equipRandomPiece(mob, chosen, slot, dropChance);
        }
    }

    private static void equipRandomPiece(Mob mob, Item item, EquipmentSlot slot, float dropChance) {
        mob.setItemSlot(slot, new ItemStack(item));
        mob.setDropChance(slot, dropChance);
    }

    private static void equipWeapon(Mob mob, Item weapon, float dropChance) {
        mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(weapon));
        mob.setDropChance(EquipmentSlot.MAINHAND, dropChance);
    }

    private static void applyFullMoonEffects(Mob mob, ThreadLocalRandom rnd) {
        double r = rnd.nextDouble();
        if (r < 0.40) {
            mob.addEffect(new MobEffectInstance(MobEffects.SPEED, 12000, 0));
        } else if (r < 0.70) {
            mob.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 12000, 0));
        } else if (r < 0.90) {
            mob.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 12000, 0));
        }
    }

    public static void onLevelTick(ServerLevel world) {
        SmartDifficultyConfig config = SmartDifficultyConfig.get();
        if (!config.enabled) return;

        long currentDay = getDay(world);
        long timeOfDay = getWorldTime(world) % 24000L;

        // 1. Day Rollover Announcement
        if (lastKnownDay != -1 && currentDay != lastKnownDay) {
            if (config.broadcastDayChanges) {
                broadcastNewDay(world, currentDay);
            }
        }
        lastKnownDay = currentDay;

        // 2. Full Moon Rise Warning (at dusk, tick 12500 - 12560)
        boolean fullMoon = isFullMoon(world);
        if (fullMoon && timeOfDay >= 12500L && timeOfDay <= 12540L) {
            if (!fullMoonAlertSent) {
                if (config.broadcastFullMoon) {
                    broadcastFullMoonAlert(world);
                }
                fullMoonAlertSent = true;
            }
        } else if (timeOfDay < 12000L || timeOfDay > 23800L) {
            fullMoonAlertSent = false;
        }

        // 3. Update Tab List Header every 20 ticks (1 second)
        long gameTick = world.getGameTime();
        if (gameTick - lastTabUpdate >= 20L) {
            updateTabList(world, currentDay, timeOfDay, fullMoon);
            lastTabUpdate = gameTick;
        }
    }

    public static void updateTabListForPlayer(ServerPlayer player) {
        // Called when a player joins so they immediately see the tab header
        ServerLevel world = (ServerLevel) player.level();
        long currentDay = getDay(world);
        long timeOfDay = getWorldTime(world) % 24000L;
        boolean fullMoon = isFullMoon(world);
        sendTabListToPlayer(player, currentDay, timeOfDay, fullMoon);
    }

    private static void updateTabList(ServerLevel world, long day, long timeOfDay, boolean fullMoon) {
        for (ServerPlayer player : world.getServer().getPlayerList().getPlayers()) {
            try {
                sendTabListToPlayer(player, day, timeOfDay, fullMoon);
            } catch (Exception ignored) {}
        }
    }

    private static void sendTabListToPlayer(ServerPlayer player, long day, long timeOfDay, boolean fullMoon) {
        int tier = getDifficultyTier(day, fullMoon);
        String moonPhase = getMoonPhaseName((ServerLevel) player.level());

        // Time of day display
        int hours = (int) ((timeOfDay / 1000 + 6) % 24);
        int minutes = (int) ((timeOfDay % 1000) * 60 / 1000);
        String ampm = hours >= 12 ? "PM" : "AM";
        int hours12 = hours % 12 == 0 ? 12 : hours % 12;
        String timeStr = String.format("%02d:%02d %s", hours12, minutes, ampm);

        // Day color based on tier
        String dayColor = switch (tier) {
            case 1 -> "§a";          // Green – early
            case 2 -> "§2";          // Dark green
            case 3 -> "§e";          // Yellow
            case 4 -> "§6";          // Gold – Iron Era
            case 5 -> "§b";          // Aqua – Diamond Era
            default -> "§d";         // Purple – Nightmare
        };

        // Tier label short form
        String tierLabel = switch (tier) {
            case 1 -> "§aEarly Survival";
            case 2 -> "§2Developing World";
            case 3 -> "§eHardened Hostiles";
            case 4 -> "§6Iron Era";
            case 5 -> "§bDiamond Era";
            default -> "§dNightmare Era";
        };

        // Moon indicator
        String moonIcon = fullMoon ? " §c☽ Full Moon" : "";

        // Build header text
        String header =
            "§8§m                                                              \n" +
            "  §f⚔ §l§6NERCT Minecraft Server§r  §7│  " + dayColor + "§lDay " + day + "§r" + moonIcon + "\n" +
            "  §7Time: §f" + timeStr + "  §8│  §7Difficulty: " + tierLabel + "\n" +
            "§8§m                                                              ";

        // Build footer text
        String footer =
            "§8§m                                                              \n" +
            "  §7Server: §fmc.nerct.dev:25565  §8│  §7Moon: §f" + moonPhase + "\n" +
            "§8§m                                                              ";

        try {
            player.connection.send(new ClientboundTabListPacket(
                Component.literal(header),
                Component.literal(footer)
            ));
        } catch (Exception ignored) {}
    }

    private static void broadcastNewDay(ServerLevel world, long day) {
        boolean fullMoon = isFullMoon(world);
        int tier = getDifficultyTier(day, fullMoon);

        String msg = "§6§l[Day " + day + "] §eA new day dawns upon the world. §7(" + getTierDescription(tier) + ")";
        for (ServerPlayer player : world.getServer().getPlayerList().getPlayers()) {
            player.sendSystemMessage(Component.literal(msg));
            player.playSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.2f);
        }
    }

    private static void broadcastFullMoonAlert(ServerLevel world) {
        for (ServerPlayer player : world.getServer().getPlayerList().getPlayers()) {
            try {
                player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 80, 20));
                player.connection.send(new ClientboundSetTitleTextPacket(Component.literal("§4§lFull Moon Rises")));
                player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("§cMonsters are enraged! Sleep is forbidden tonight.")));
                player.playSound(SoundEvents.RAID_HORN.value(), 1.0f, 0.8f);
                player.sendSystemMessage(Component.literal("§4§l[Full Moon] §cThe blood moon ascends! Stronger monsters roam the night and sleeping in beds is disabled!"));
            } catch (Exception ignored) {}
        }
    }
}
