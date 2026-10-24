package com.shokiteufel.shokimod.mixin;

import com.shokiteufel.shokimod.handler.ParticleTracer;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Jedes Partikel-Paket, das der Server schickt, fuer den Partikel-Mitschreiber (Einstellungen, Reiter "Particles").
 * Er schreibt nur mit und greift nicht ein; ausgeschaltet kostet der Aufruf einen Feldzugriff.
 */
@Mixin(ClientPacketListener.class)
public class ParticleTraceMixin {

    @Inject(method = "handleParticleEvent", at = @At("HEAD"))
    private void shokimod$traceParticle(ClientboundLevelParticlesPacket packet, CallbackInfo ci) {
        ParticleTracer.onPacket(packet);
    }
}
