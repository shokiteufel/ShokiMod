package com.shokiteufel.shokimod.mixin;

import com.shokiteufel.shokimod.handler.HotspotTracker;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Die rosa Randpartikel eines Hotspots: aus ihnen liest der Tracker den Radius, und wer
 * will, bekommt sie danach nicht mehr zu sehen. Laeuft im Netzwerk-Thread - der Tracker
 * rechnet dort nur und fasst die Welt nicht an.
 */
@Mixin(ClientPacketListener.class)
public class HotspotParticleMixin {

    @Inject(method = "handleParticleEvent", at = @At("HEAD"), cancellable = true)
    private void shokimod$onHotspotParticle(ClientboundLevelParticlesPacket packet, CallbackInfo ci) {
        if (HotspotTracker.onParticle(packet)) ci.cancel();
    }
}
