package com.shokiteufel.shokimod.mixin;

import com.shokiteufel.shokimod.handler.FishingHookDisplay;
import com.shokiteufel.shokimod.handler.HookSounds;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Der Einsprung fuer alles, was die Toene beim Angeln betrifft: das Fishing log, der Alarm aus dem Biss-Ton und die Lautstaerke ("Quiet other sounds").
 *
 * Bis 2.6.19 sass er in {@code SoundManager.play}. Das war zu frueh: Toene, die weiter als zehn Bloecke weg sind - die der Seekreaturen im Gefecht -, spielt
 * {@code ClientLevel} ueber {@code SoundManager.playDelayed} ab (der Schall braucht Zeit). Sie laufen spaeter, aus {@code SoundEngine.tick}, direkt in
 * {@code SoundEngine.play} und kommen an {@code SoundManager.play} nie vorbei: Der User hoerte Seacreature-Toene trotz "Quiet other sounds" (28.11., am Bytecode von
 * 26.3 nachgesehen). {@code SoundEngine.play} ist die eine Stelle, durch die jeder Ton genau einmal geht - dieselbe, an der die Stummschaltung
 * ({@link SoundMuteMixin}) sitzt.
 *
 * Mit {@code require = 0}: Aendert sich die Methode in einer spaeteren Fassung, faellt nur dieses Werkzeug weg, und das Spiel startet trotzdem.
 */
@Mixin(SoundEngine.class)
public class HookSoundMixin {

    @Inject(method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)Lnet/minecraft/client/sounds/SoundEngine$PlayResult;",
            at = @At("HEAD"), require = 0)
    private void shokimod$hookSounds(SoundInstance sound, CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
        // Das Fishing log schreibt mit, welche Toene beim Angeln erklingen, und der Alarm hoert auf den Biss-Ton (beides nur, wenn es an ist)
        FishingHookDisplay.onSound(sound);
        // Fishing > Hook display > Quiet other sounds: erst nach dem Log und dem Alarm, die sehen den Ton, wie er ankam
        HookSounds.onPlay(sound);
    }
}
