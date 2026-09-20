package com.notooexpensive.mixin;

import com.notooexpensive.NoTooExpensiveConfig;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.ItemCombinerMenuSlotDefinition;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin extends ItemCombinerMenu {

    public AnvilMenuMixin(MenuType<?> menuType, int containerId, Inventory inventory, ContainerLevelAccess access, ItemCombinerMenuSlotDefinition slotDefinition) {
        super(menuType, containerId, inventory, access, slotDefinition);
    }

    @Redirect(
        method = "createResult",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/inventory/DataSlot;set(I)V"
        )
    )
    private void notooexpensive$capAnvilCost(DataSlot slot, int value) {
        ItemStack firstSlot = this.inputSlots.getItem(0);
        int maxCost = NoTooExpensiveConfig.getMaxAnvilCost();
        // If the first slot contains a stack (count > 1), vanilla deliberately sets cost to 40
        // to prevent enchanting stacked items. We preserve that vanilla check.
        if (firstSlot.getCount() <= 1 && value > maxCost) {
            slot.set(maxCost);
        } else {
            slot.set(value);
        }
    }

    @Inject(
        method = "calculateIncreasedRepairCost",
        at = @At("HEAD"),
        cancellable = true
    )
    private static void notooexpensive$capPriorWorkPenalty(int oldCost, CallbackInfoReturnable<Integer> cir) {
        int maxPenalty = NoTooExpensiveConfig.getMaxPriorWorkPenalty();
        long nextCost = (long) oldCost * 2L + 1L;
        if (nextCost > (long) maxPenalty) {
            cir.setReturnValue(maxPenalty);
        } else if (nextCost < 0L) {
            cir.setReturnValue(0);
        } else {
            cir.setReturnValue((int) nextCost);
        }
    }
}
