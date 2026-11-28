package com.shokiteufel.shokimod.mixin;

import com.shokiteufel.shokimod.handler.SoundTracer;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Jeder Ton, den das Spiel abspielt, geht hier vorbei. Der Sound-Spuerer ({@link SoundTracer})
 * sieht nach, wer ihn gerade ausloest - und tut nichts, solange er in den Einstellungen aus ist.
 *
 * Mit {@code require = 0}: Aendert sich die Methode in einer spaeteren Fassung, faellt nur dieses
 * Werkzeug weg, und das Spiel startet trotzdem.
 */
@Mixin(SoundManager.class)
public class SoundManagerMixin {

    @Inject(method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)Lnet/minecraft/client/sounds/SoundEngine$PlayResult;",
            at = @At("HEAD"), require = 0)
    private void shokimod$trace(SoundInstance sound, CallbackInfoReturnable<?> cir) {
        SoundTracer.onPlay(sound);
        // Das Fishing log, der Alarm aus dem Biss-Ton und die Lautstaerke beim Angeln sitzen seit 2.6.20 in SoundEngine.play (HookSoundMixin): Toene, die
        // weiter als zehn Bloecke weg sind, laufen ueber SoundManager.playDelayed und kamen hier nie vorbei
    }
}
