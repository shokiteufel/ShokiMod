package com.shokiteufel.shokimod.mixin;

import com.shokiteufel.shokimod.handler.ParticleForce;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fuer "Show always": erzeugt ein Partikel selbst, bevor ein Filter (Sodium Extra) es abbrechen kann.
 *
 * Die Prioritaet 500 liegt unter dem Standard 1000: Je kleiner die Zahl, desto frueher laeuft der Einsprung. Sodium Extra
 * haengt sich mit dem Standard an denselben Punkt ({@code createParticle}, Anfang, abbrechbar) - wer hier zuerst einen
 * Rueckgabewert setzt und abbricht, nach dem laufen die anderen Einspruenge dieses Punktes nicht mehr.
 *
 * Nur wenn {@link ParticleForce#armed()} steht - also waehrend ein Paket verarbeitet wird, das zu einem Item auf "Show always"
 * gehoert. Sonst kostet der Aufruf einen Feldzugriff. {@code require = 0}: Heisst die Methode auf einer Version anders, faellt
 * nur dieses Feature aus, das Spiel startet trotzdem.
 */
@Mixin(value = ParticleEngine.class, priority = 500)
public class ParticleEngineForceMixin {

    @Inject(method = "createParticle", at = @At("HEAD"), cancellable = true, require = 0)
    private void shokimod$forceShow(ParticleOptions options, double x, double y, double z, double xd, double yd, double zd,
                                    CallbackInfoReturnable<Particle> cir) {
        if (!ParticleForce.armed()) return;
        Particle particle = ParticleForce.create((ParticleEngine) (Object) this, options, x, y, z, xd, yd, zd);
        if (particle != null) cir.setReturnValue(particle);
    }
}
