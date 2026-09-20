package com.modenforcer.mixin;

import com.modenforcer.ModEnforcerConfig;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class ServerCommonPacketListenerMixin {
    @Shadow private boolean keepAlivePending;
    @Shadow private long keepAliveTime;
    @Shadow private long keepAliveChallenge;
    @Shadow private boolean closed;
    @Shadow public abstract boolean isSingleplayerOwner();

    @Shadow public abstract void send(net.minecraft.network.protocol.Packet<?> packet);

    @Inject(method = "keepConnectionAlive", at = @At("HEAD"))
    private void modenforcer$fastKeepAlive(CallbackInfo ci) {
        if (!((Object) this instanceof ServerGamePacketListenerImpl)) {
            return;
        }
        if (this.closed || this.isSingleplayerOwner()) {
            return;
        }
        ModEnforcerConfig config = ModEnforcerConfig.get();
        if (config != null && config.tabPingEnabled && config.fastPingRefresh && !this.keepAlivePending) {
            long time = Util.getMillis();
            long interval = Math.max(500L, config.pingRefreshIntervalMs);
            if (time - this.keepAliveTime >= interval) {
                this.keepAlivePending = true;
                this.keepAliveTime = time;
                this.keepAliveChallenge = time;
                try {
                    this.send(new ClientboundKeepAlivePacket(this.keepAliveChallenge));
                } catch (Throwable ignored) {}
            }
        }
    }
}
