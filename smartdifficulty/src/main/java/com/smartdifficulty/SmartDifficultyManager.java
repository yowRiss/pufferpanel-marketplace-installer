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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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
        // Base chance that this mob gets any armor pieces at all
        double armorSpawnChance = switch (tier) {
            case 1 -> 0.20;
            case 2 -> 0.40;
            case 3 -> 0.65;
            case 4 -> 0.85; // Day 100+
            case 5 -> 0.95; // Day 200+
            default -> 0.98; // Day 300+
        };
        if (fullMoon) armorSpawnChance = Math.min(1.0, armorSpawnChance + 0.15);

        if (rnd.nextDouble() < armorSpawnChance) {
            // Determine how many armor pieces to equip (1 to 4):
            // Random distribution so mobs aren't always in full armor!
            // Zombies can spawn with just a helmet, just a chestplate, 2 pieces, 3 pieces, or full armor.
            int pieceCount;
            double countRoll = rnd.nextDouble();
            if (tier <= 2) {
                // Early game: 75% 1 piece, 25% 2 pieces
                pieceCount = countRoll < 0.75 ? 1 : 2;
            } else if (tier == 3) {
                // Day 50-99: 40% 1 piece, 35% 2 pieces, 25% 3 pieces
                if (countRoll < 0.40) pieceCount = 1;
                else if (countRoll < 0.75) pieceCount = 2;
                else pieceCount = 3;
            } else if (tier == 4) {
                // Day 100+: Iron Era
                // User explicit: random piece count (e.g. only chestplate or only helmet, not always full armor)
                // 30% only 1 piece, 30% 2 pieces, 25% 3 pieces, 15% full armor
                if (countRoll < 0.30) pieceCount = 1;
                else if (countRoll < 0.60) pieceCount = 2;
                else if (countRoll < 0.85) pieceCount = 3;
                else pieceCount = 4;
            } else {
                // Day 200+ (Diamond Era) & Endgame:
                // User explicit: "bisa full armor atau cuma 1 armor pokoknya random apa yg di pakai"
                // 25% only 1 piece (e.g. just a diamond chestplate!), 30% 2 pieces, 25% 3 pieces, 20% full armor
                if (countRoll < 0.25) pieceCount = 1;
                else if (countRoll < 0.55) pieceCount = 2;
                else if (countRoll < 0.80) pieceCount = 3;
                else pieceCount = 4;
            }

            // Shuffle all 4 armor slots to pick which specific pieces are worn
            List<EquipmentSlot> slots = new ArrayList<>(Arrays.asList(
                EquipmentSlot.HEAD,
                EquipmentSlot.CHEST,
                EquipmentSlot.LEGS,
                EquipmentSlot.FEET
            ));
            Collections.shuffle(slots, rnd);

            for (int i = 0; i < pieceCount; i++) {
                EquipmentSlot slot = slots.get(i);
                String material = pickArmorMaterial(tier, rnd);
                Item item = getArmorItem(slot, material);
                if (item != null) {
                    equipRandomPiece(mob, item, slot, dropChance);
                }
            }
        }

        // Weapons for zombies
        if (mob instanceof Zombie) {
            double weaponChance = switch (tier) {
                case 1 -> 0.10;
                case 2 -> 0.25;
                case 3 -> 0.45;
                case 4 -> 0.65;
                case 5 -> 0.80;
                default -> 0.90;
            };
            if (rnd.nextDouble() < weaponChance) {
                Item weapon;
                if (tier <= 2) {
                    weapon = rnd.nextBoolean() ? Items.STONE_SWORD : Items.COPPER_SWORD;
                } else if (tier == 3) {
                    weapon = rnd.nextBoolean() ? Items.COPPER_SWORD : Items.IRON_SWORD;
                } else if (tier == 4) {
                    Item[] t4Weapons = {Items.IRON_SWORD, Items.IRON_AXE, Items.COPPER_SWORD, Items.COPPER_AXE};
                    weapon = t4Weapons[rnd.nextInt(t4Weapons.length)];
                } else if (tier == 5) {
                    // Day 200+: User explicit: "pokoknya random apa yg di pakai bisa full diamon tapi copper sword"
                    double wRoll = rnd.nextDouble();
                    if (wRoll < 0.35) {
                        weapon = Items.DIAMOND_SWORD;
                    } else if (wRoll < 0.65) {
                        weapon = Items.COPPER_SWORD;
                    } else if (wRoll < 0.85) {
                        weapon = Items.IRON_SWORD;
                    } else {
                        weapon = rnd.nextBoolean() ? Items.DIAMOND_AXE : Items.COPPER_SPEAR;
                    }
                } else {
                    double wRoll = rnd.nextDouble();
                    weapon = wRoll < 0.25 ? Items.NETHERITE_SWORD : (wRoll < 0.60 ? Items.DIAMOND_SWORD : Items.COPPER_SWORD);
                }
                equipWeapon(mob, weapon, dropChance);
            }
        }
    }

    private static String pickArmorMaterial(int tier, ThreadLocalRandom rnd) {
        double r = rnd.nextDouble();
        return switch (tier) {
            case 1 -> r < 0.70 ? "leather" : "gold";
            case 2 -> {
                if (r < 0.30) yield "leather";
                if (r < 0.55) yield "gold";
                if (r < 0.80) yield "chainmail";
                yield "copper";
            }
            case 3 -> {
                if (r < 0.35) yield "copper";
                if (r < 0.70) yield "chainmail";
                yield "iron";
            }
            case 4 -> {
                // Day 100+: Iron Era (high iron bias, with copper/chainmail variation)
                if (r < 0.75) yield "iron";
                if (r < 0.90) yield "copper";
                yield "chainmail";
            }
            case 5 -> {
                // Day 200+: Diamond Era (each piece independently rolls Diamond, Iron, or Copper!)
                if (r < 0.45) yield "diamond";
                if (r < 0.85) yield "iron";
                yield "copper";
            }
            default -> {
                // Tier 6: Day 300+ Endgame
                if (r < 0.25) yield "netherite";
                if (r < 0.80) yield "diamond";
                yield "iron";
            }
        };
    }

    private static Item getArmorItem(EquipmentSlot slot, String material) {
        return switch (material) {
            case "leather" -> switch (slot) {
                case HEAD -> Items.LEATHER_HELMET;
                case CHEST -> Items.LEATHER_CHESTPLATE;
                case LEGS -> Items.LEATHER_LEGGINGS;
                case FEET -> Items.LEATHER_BOOTS;
                default -> null;
            };
            case "gold" -> switch (slot) {
                case HEAD -> Items.GOLDEN_HELMET;
                case CHEST -> Items.GOLDEN_CHESTPLATE;
                case LEGS -> Items.GOLDEN_LEGGINGS;
                case FEET -> Items.GOLDEN_BOOTS;
                default -> null;
            };
            case "chainmail" -> switch (slot) {
                case HEAD -> Items.CHAINMAIL_HELMET;
                case CHEST -> Items.CHAINMAIL_CHESTPLATE;
                case LEGS -> Items.CHAINMAIL_LEGGINGS;
                case FEET -> Items.CHAINMAIL_BOOTS;
                default -> null;
            };
            case "copper" -> switch (slot) {
                case HEAD -> Items.COPPER_HELMET;
                case CHEST -> Items.COPPER_CHESTPLATE;
                case LEGS -> Items.COPPER_LEGGINGS;
                case FEET -> Items.COPPER_BOOTS;
                default -> null;
            };
            case "iron" -> switch (slot) {
                case HEAD -> Items.IRON_HELMET;
                case CHEST -> Items.IRON_CHESTPLATE;
                case LEGS -> Items.IRON_LEGGINGS;
                case FEET -> Items.IRON_BOOTS;
                default -> null;
            };
            case "diamond" -> switch (slot) {
                case HEAD -> Items.DIAMOND_HELMET;
                case CHEST -> Items.DIAMOND_CHESTPLATE;
                case LEGS -> Items.DIAMOND_LEGGINGS;
                case FEET -> Items.DIAMOND_BOOTS;
                default -> null;
            };
            case "netherite" -> switch (slot) {
                case HEAD -> Items.NETHERITE_HELMET;
                case CHEST -> Items.NETHERITE_CHESTPLATE;
                case LEGS -> Items.NETHERITE_LEGGINGS;
                case FEET -> Items.NETHERITE_BOOTS;
                default -> null;
            };
            default -> null;
        };
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
