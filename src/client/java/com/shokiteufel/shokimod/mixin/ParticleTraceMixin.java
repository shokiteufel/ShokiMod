package com.shokiteufel.shokimod.mixin;

import com.shokiteufel.shokimod.handler.ParticleForce;
import com.shokiteufel.shokimod.handler.ParticleHider;
import com.shokiteufel.shokimod.handler.ParticleTracer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Jedes Partikel-Paket, das der Server schickt: erst fuer den Partikel-Mitschreiber (Einstellungen, Reiter "Particles"), dann
 * fuer das Ausblenden einzelner Items. Der Mitschreiber greift nicht ein; das Ausblenden verwirft nur Pakete, die zu einem
 * eingeschalteten Schalter passen. Ausgeschaltet kostet der Aufruf je Paket wenige Feldzugriffe.
 */
@Mixin(ClientPacketListener.class)
public class ParticleTraceMixin {

    @Inject(method = "handleParticleEvent", at = @At("HEAD"), cancellable = true)
    private void shokimod$traceParticle(ClientboundLevelParticlesPacket packet, CallbackInfo ci) {
        // Erst mitschreiben, dann entscheiden: Der Mitschreiber soll auch zeigen, was der Server geschickt hat und die
        // Einstellung dann ausblendet
        ParticleTracer.onPacket(packet);
        ParticleHider.Action action = ParticleHider.decide(packet);
        if (action == ParticleHider.Action.HIDE) {
            ci.cancel();
        } else if (action == ParticleHider.Action.FORCE) {
            // "Show always": Waehrend dieses Paket verarbeitet wird, erzeugt ParticleEngineForceMixin die Partikel selbst
            ParticleForce.arm();
        }
    }

    /** Die Markierung gilt nur fuer dieses eine Paket */
    @Inject(method = "handleParticleEvent", at = @At("RETURN"))
    private void shokimod$disarm(ClientboundLevelParticlesPacket packet, CallbackInfo ci) {
        if (Minecraft.getInstance().isSameThread()) ParticleForce.disarm();
    }
}
