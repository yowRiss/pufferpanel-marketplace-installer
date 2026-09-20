package com.modenforcer.mixin;

import com.modenforcer.TabPingManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerTabMixin {

    @Inject(method = "getTabListDisplayName", at = @At("HEAD"), cancellable = true)
    private void modenforcer$onGetTabListDisplayName(CallbackInfoReturnable<Component> cir) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        Component pingDisplayName = TabPingManager.getDisplayName(player);
        if (pingDisplayName != null) {
            cir.setReturnValue(pingDisplayName);
        }
    }
}
