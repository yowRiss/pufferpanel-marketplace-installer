package com.autoclicker;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class AutoClickSession {
    public enum Mode {
        ATTACK,
        MINE
    }

    private final UUID playerUuid;
    private Mode mode;
    private int customAttackInterval = 0; // 0 = auto based on weapon cooldown
    private int attackTimer = 0;
    private int ticksActive = 0;

    private BlockPos currentMiningPos = null;
    private float miningProgress = 0.0f;

    private boolean toolProtection = true;
    private boolean pvp = false;

    public AutoClickSession(UUID playerUuid, Mode mode, int customAttackInterval) {
        this.playerUuid = playerUuid;
        this.mode = mode;
        this.customAttackInterval = customAttackInterval;
    }

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
        this.currentMiningPos = null;
        this.miningProgress = 0.0f;
        this.attackTimer = 0;
    }

    public void setCustomAttackInterval(int interval) {
        this.customAttackInterval = interval;
    }

    public int getCustomAttackInterval() {
        return customAttackInterval;
    }

    public boolean isToolProtection() {
        return toolProtection;
    }

    public void setToolProtection(boolean toolProtection) {
        this.toolProtection = toolProtection;
    }

    public boolean isPvp() {
        return pvp;
    }

    public void setPvp(boolean pvp) {
        this.pvp = pvp;
    }

    public void tick(ServerPlayer player) {
        if (!player.isAlive() || player.isRemoved()) {
            AutoClickManager.stop(player.getUUID());
            return;
        }

        ticksActive++;
        ServerLevel level = player.level();

        if (mode == Mode.ATTACK) {
            tickAttack(player, level);
        } else if (mode == Mode.MINE) {
            tickMine(player, level);
        }
    }

    private void tickAttack(ServerPlayer player, ServerLevel level) {
        attackTimer++;

        // Tool protection check
        ItemStack weapon = player.getMainHandItem();
        if (toolProtection && weapon.isDamageableItem()) {
            int remaining = weapon.getMaxDamage() - weapon.getDamageValue();
            if (remaining <= 2) {
                player.sendSystemMessage(Component.literal("§c[AutoClicker] Stopped! Durability senjata Anda hampir habis (" + remaining + " uses tersisa)."));
                AutoClickManager.stop(player.getUUID());
                return;
            }
        }

        // Determine if ready to strike
        boolean readyToStrike = false;
        if (customAttackInterval > 0) {
            if (attackTimer >= customAttackInterval) {
                readyToStrike = true;
            }
        } else {
            // Automatic cooldown: wait until attack strength scale reaches full (>= 0.95f)
            float scale = player.getAttackStrengthScale(0.5f);
            if (scale >= 0.95f || attackTimer >= 15) {
                readyToStrike = true;
            }
        }

        Entity target = findTargetEntity(player, level, 3.5, pvp);

        if (readyToStrike) {
            attackTimer = 0;
            player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);

            if (target != null) {
                player.attack(target);
                player.resetAttackStrengthTicker();
            }
        }

        // Actionbar HUD every 40 ticks (~2 seconds)
        if (ticksActive % 40 == 0) {
            String targetName = (target != null) ? target.getName().getString() : "Menunggu target...";
            String speedStr = (customAttackInterval > 0) ? (customAttackInterval + " ticks") : "Auto Cooldown";
            player.sendSystemMessage(Component.literal("§6⚔ AutoClicker: §aATTACK §7(" + targetName + " | " + speedStr + " | §e/ac off§7)"), true);
        }
    }

    private void tickMine(ServerPlayer player, ServerLevel level) {
        // Tool protection check
        ItemStack tool = player.getMainHandItem();
        if (toolProtection && tool.isDamageableItem()) {
            int remaining = tool.getMaxDamage() - tool.getDamageValue();
            if (remaining <= 2) {
                clearMiningProgress(player, level);
                player.sendSystemMessage(Component.literal("§c[AutoClicker] Stopped! Durability tool Anda hampir habis (" + remaining + " uses tersisa)."));
                AutoClickManager.stop(player.getUUID());
                return;
            }
        }

        // Raycast block (reach 4.5)
        Vec3 eyePos = player.getEyePosition();
        Vec3 viewVec = player.getViewVector(1.0f);
        Vec3 endPos = eyePos.add(viewVec.scale(4.5));
        ClipContext clipCtx = new ClipContext(eyePos, endPos, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player);
        BlockHitResult hit = level.clip(clipCtx);

        if (hit.getType() != HitResult.Type.BLOCK) {
            clearMiningProgress(player, level);
            if (ticksActive % 40 == 0) {
                player.sendSystemMessage(Component.literal("§6⛏ AutoClicker: §aMINE §7(Tidak ada blok dalam jangkauan | §e/ac off§7)"), true);
            }
            return;
        }

        BlockPos targetPos = hit.getBlockPos();
        BlockState state = level.getBlockState(targetPos);

        if (state.isAir() || state.getDestroySpeed(level, targetPos) < 0.0f) {
            clearMiningProgress(player, level);
            return;
        }

        // Check if block changed
        if (currentMiningPos == null || !currentMiningPos.equals(targetPos)) {
            clearMiningProgress(player, level);
            currentMiningPos = targetPos;
            miningProgress = 0.0f;
        }

        if (player.isCreative()) {
            if (ticksActive % 5 == 0) {
                player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
                player.gameMode.destroyBlock(targetPos);
                currentMiningPos = null;
                miningProgress = 0.0f;
            }
        } else {
            // Survival / Adventure
            if (ticksActive % 4 == 0) {
                player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
            }

            float step = state.getDestroyProgress(player, level, targetPos);
            if (step >= 1.0f) {
                // Insta-mine
                player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
                level.destroyBlockProgress(player.getId(), targetPos, -1);
                player.gameMode.destroyBlock(targetPos);
                currentMiningPos = null;
                miningProgress = 0.0f;
            } else {
                miningProgress += step;
                if (miningProgress >= 1.0f) {
                    level.destroyBlockProgress(player.getId(), targetPos, -1);
                    player.gameMode.destroyBlock(targetPos);
                    currentMiningPos = null;
                    miningProgress = 0.0f;
                } else {
                    int stage = (int) (miningProgress * 10.0f);
                    level.destroyBlockProgress(player.getId(), targetPos, Math.min(stage, 9));
                }
            }
        }

        if (ticksActive % 40 == 0) {
            String blockName = state.getBlock().getName().getString();
            player.sendSystemMessage(Component.literal("§6⛏ AutoClicker: §aMINE §7(" + blockName + " | §e/ac off§7)"), true);
        }
    }

    public void cleanup(ServerPlayer player) {
        if (player != null && currentMiningPos != null) {
            clearMiningProgress(player, player.level());
        }
    }

    private void clearMiningProgress(ServerPlayer player, ServerLevel level) {
        if (currentMiningPos != null) {
            level.destroyBlockProgress(player.getId(), currentMiningPos, -1);
            currentMiningPos = null;
            miningProgress = 0.0f;
        }
    }

    public static Entity findTargetEntity(ServerPlayer player, ServerLevel level, double reach, boolean allowPlayers) {
        Vec3 eyePos = player.getEyePosition();
        Vec3 viewVec = player.getViewVector(1.0f);
        Vec3 reachVec = eyePos.add(viewVec.scale(reach));
        AABB box = player.getBoundingBox().expandTowards(viewVec.scale(reach)).inflate(1.0);

        List<Entity> candidates = level.getEntities(player, box, entity -> {
            if (entity.isSpectator() || !entity.isPickable() || !entity.isAlive()) {
                return false;
            }
            if (!(entity instanceof LivingEntity) || (entity instanceof ArmorStand)) {
                return false;
            }
            if (!allowPlayers && (entity instanceof net.minecraft.world.entity.player.Player)) {
                return false;
            }
            return true;
        });

        Entity closest = null;
        double closestDist = reach * reach;

        for (Entity e : candidates) {
            AABB bb = e.getBoundingBox().inflate(e.getPickRadius());
            Optional<Vec3> hit = bb.clip(eyePos, reachVec);
            if (hit.isPresent()) {
                double dist = eyePos.distanceToSqr(hit.get());
                if (dist < closestDist) {
                    closestDist = dist;
                    closest = e;
                }
            }
        }
        return closest;
    }
}
