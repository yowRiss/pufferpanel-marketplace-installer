package com.homingexp;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class HomingExpManager {

    public static class TrackedOrb {
        public final ExperienceOrb orb;
        public final UUID targetPlayerUuid;
        public final boolean isExclusive;
        public int ticksAlive;
        public Vec3 lastPos;
        public int stuckTicks;

        public TrackedOrb(ExperienceOrb orb, UUID targetPlayerUuid, boolean isExclusive) {
            this.orb = orb;
            this.targetPlayerUuid = targetPlayerUuid;
            this.isExclusive = isExclusive;
            this.ticksAlive = 0;
            this.lastPos = orb.position();
            this.stuckTicks = 0;
        }
    }

    private static final Map<Integer, TrackedOrb> homingOrbs = new ConcurrentHashMap<>();
    private static final Set<Integer> looseOrbs = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static long tickCounter = 0;

    public static void onEntityLoad(Entity entity, ServerLevel world) {
        if (entity instanceof ExperienceOrb orb) {
            int id = orb.getId();
            if (!homingOrbs.containsKey(id)) {
                looseOrbs.add(id);
            }
        }
    }

    public static void onEntityUnload(Entity entity, ServerLevel world) {
        if (entity instanceof ExperienceOrb orb) {
            int id = orb.getId();
            homingOrbs.remove(id);
            looseOrbs.remove(id);
        }
    }

    public static void trackHomingOrb(ExperienceOrb orb, UUID playerUuid, boolean isExclusive) {
        if (orb == null || orb.isRemoved()) return;
        int id = orb.getId();
        orb.setNoGravity(true);
        homingOrbs.put(id, new TrackedOrb(orb, playerUuid, isExclusive));
        looseOrbs.remove(id);
    }

    public static ServerPlayer resolvePlayerKiller(Entity killerEntity, DamageSource damageSource, LivingEntity victim) {
        if (killerEntity instanceof ServerPlayer player) {
            return player;
        }
        if (damageSource != null) {
            Entity attacker = damageSource.getEntity();
            if (attacker instanceof ServerPlayer player) {
                return player;
            }
            Entity direct = damageSource.getDirectEntity();
            if (direct instanceof Projectile projectile && projectile.getOwner() instanceof ServerPlayer player) {
                return player;
            }
            if (attacker instanceof TamableAnimal tamed && tamed.getOwner() instanceof ServerPlayer player) {
                return player;
            }
        }
        if (victim != null && victim.getLastHurtByMob() instanceof ServerPlayer player) {
            return player;
        }
        return null;
    }

    public static void onMobKilled(ServerLevel world, Entity killerEntity, LivingEntity victim, DamageSource damageSource) {
        if (victim == null || world == null) return;
        HomingExpConfig config = HomingExpConfig.get();
        if (!config.enabled) return;

        // Check if mob drops are enabled
        if (!world.getGameRules().get(GameRules.MOB_DROPS)) {
            return;
        }

        // Avoid double processing if already consumed
        if (victim.wasExperienceConsumed()) {
            return;
        }

        ServerPlayer killer = resolvePlayerKiller(killerEntity, damageSource, victim);
        if (killer == null) {
            return;
        }

        if (!config.isPlayerEnabled(killer.getUUID())) {
            return;
        }

        double dist = killer.distanceTo(victim);
        if (dist > config.maxRange) {
            return;
        }

        int xp = victim.getExperienceReward(world, killer);
        if (xp <= 0) {
            return;
        }

        // Mark experience as consumed so vanilla LivingEntity.die() won't drop duplicate orbs
        victim.skipDropExperience();

        String mode = config.getPlayerMode(killer.getUUID());
        if ("hybrid".equals(mode)) {
            if (dist > config.hybridDirectDistance) {
                mode = "direct";
            } else {
                mode = "homing";
            }
        }

        if ("direct".equals(mode)) {
            // Drop directly at player's location for instant pickup
            killer.takeXpDelay = 0;
            Vec3 dropPos = killer.position().add(0, 0.2, 0);
            ExperienceOrb.award(world, dropPos, xp);
            if (config.showParticles) {
                world.sendParticles(ParticleTypes.HAPPY_VILLAGER, killer.getX(), killer.getY() + 0.5, killer.getZ(), 5, 0.3, 0.3, 0.3, 0.02);
            }
        } else {
            // Homing mode: spawn orbs at mob position aimed towards killer
            Vec3 spawnPos = victim.position().add(0, 0.5, 0);
            Vec3 toKiller = killer.position().subtract(spawnPos);
            Vec3 initDir = toKiller.lengthSqr() > 0.001 ? toKiller.normalize().scale(0.8) : new Vec3(0, 0.2, 0);

            while (xp > 0) {
                int orbVal = ExperienceOrb.getExperienceValue(xp);
                xp -= orbVal;
                ExperienceOrb orb = new ExperienceOrb(world, spawnPos, initDir, orbVal);
                orb.setNoGravity(true);
                world.addFreshEntity(orb);
                trackHomingOrb(orb, killer.getUUID(), true);
            }
        }
    }

    public static void onWorldTick(ServerLevel world) {
        tickCounter++;
        HomingExpConfig config = HomingExpConfig.get();
        if (!config.enabled) return;

        // 1. Process active homing orbs
        Iterator<Map.Entry<Integer, TrackedOrb>> it = homingOrbs.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, TrackedOrb> entry = it.next();
            TrackedOrb tracked = entry.getValue();
            ExperienceOrb orb = tracked.orb;

            if (orb.isRemoved() || orb.level() != world) {
                it.remove();
                continue;
            }

            ServerPlayer target = world.getServer().getPlayerList().getPlayer(tracked.targetPlayerUuid);
            if (target == null || !target.isAlive() || target.isSpectator() || target.level() != world) {
                if (tracked.isExclusive) {
                    orb.setNoGravity(false);
                    it.remove();
                    continue;
                } else {
                    it.remove();
                    continue;
                }
            }

            Vec3 targetCenter = target.position().add(0, target.getEyeHeight() * 0.5, 0);
            Vec3 orbPos = orb.position();
            Vec3 toTarget = targetCenter.subtract(orbPos);
            double dist = toTarget.length();

            if (dist > config.maxRange * 1.5) {
                orb.setNoGravity(false);
                it.remove();
                continue;
            }

            if (dist <= 1.3) {
                // Absorbed!
                target.takeXpDelay = 0;
                orb.playerTouch(target);
                if (!orb.isRemoved()) {
                    int val = orb.getValue();
                    if (val > 0) target.giveExperiencePoints(val);
                    orb.discard();
                }
                it.remove();
                continue;
            }

            tracked.ticksAlive++;
            Vec3 dir = toTarget.normalize();

            // Detect if orb is stuck in a block/obstacle
            if (tracked.lastPos != null && orbPos.distanceToSqr(tracked.lastPos) < 0.0005) {
                tracked.stuckTicks++;
            } else {
                tracked.stuckTicks = 0;
            }
            tracked.lastPos = orbPos;

            double speed = Math.min(2.5, Math.max(0.6, dist * 0.075 * config.homingSpeed));
            if (tracked.stuckTicks > 15) {
                // Overcome obstacle with higher boost
                speed = Math.max(speed, 1.4);
            }

            if (tracked.stuckTicks > 50 || tracked.ticksAlive > 140) {
                // Teleport directly to player if stuck or flying for > 7 seconds
                orb.setPos(targetCenter.x - dir.x * 0.5, targetCenter.y, targetCenter.z - dir.z * 0.5);
                tracked.stuckTicks = 0;
            } else {
                Vec3 vel = dir.scale(speed);
                orb.setDeltaMovement(vel);
                orb.setNoGravity(true);
                Vec3 newPos = orbPos.add(vel);
                orb.setPos(newPos.x, newPos.y, newPos.z);
            }

            if (config.showParticles && (tracked.ticksAlive % 2 == 0)) {
                world.sendParticles(ParticleTypes.HAPPY_VILLAGER, orb.getX(), orb.getY() + 0.1, orb.getZ(), 1, 0.04, 0.04, 0.04, 0.0);
            }
        }

        // 2. Loose orbs magnet (runs every 4 ticks to optimize CPU)
        if (config.magnetLooseXp && (tickCounter % 4 == 0) && !looseOrbs.isEmpty()) {
            Iterator<Integer> looseIt = looseOrbs.iterator();
            while (looseIt.hasNext()) {
                int orbId = looseIt.next();
                Entity entity = world.getEntity(orbId);
                if (!(entity instanceof ExperienceOrb orb) || orb.isRemoved()) {
                    looseIt.remove();
                    continue;
                }

                if (homingOrbs.containsKey(orbId)) {
                    looseIt.remove();
                    continue;
                }

                // Find nearest eligible player
                ServerPlayer nearest = null;
                double nearestDistSq = config.looseXpRange * config.looseXpRange;

                for (ServerPlayer player : world.players()) {
                    if (player.isSpectator() || !player.isAlive()) continue;
                    if (!config.isPlayerEnabled(player.getUUID())) continue;

                    double d2 = player.distanceToSqr(orb);
                    if (d2 < nearestDistSq) {
                        nearestDistSq = d2;
                        nearest = player;
                    }
                }

                if (nearest != null) {
                    trackHomingOrb(orb, nearest.getUUID(), false);
                    looseIt.remove();
                }
            }
        }
    }
}
