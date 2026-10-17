package com.shokiteufel.shokimod.mixin;

import com.shokiteufel.shokimod.data.FeatureGate;
import com.shokiteufel.shokimod.handler.SoundMute;

import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.resources.Identifier;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Verschluckt die Toene, die in den Einstellungen unter Fishing > Mute stumm geschaltet sind.
 *
 * Dieselbe Stelle wie bei Feesh, {@code SoundEngine.play}: Hier laufen alle Toene durch, auch die, die der
 * Server schickt. Statt nichts zurueckzugeben meldet der Mixin "nicht gestartet" - das ist ein gueltiger Wert,
 * mit dem jeder Aufrufer rechnen muss. Am Bytecode von 26.1.2, 26.2 und 26.3 nachgesehen: Methode und
 * Rueckgabewert sind in allen dreien gleich.
 */
@Mixin(SoundEngine.class)
public class SoundMuteMixin {

    @Inject(method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)Lnet/minecraft/client/sounds/SoundEngine$PlayResult;",
            at = @At("HEAD"), cancellable = true)
    private void shokimod$mute(SoundInstance sound, CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
        if (!FeatureGate.mute()) return;
        try {
            Identifier id = sound.getIdentifier();
            // getVolume() rechnet mit dem noch nicht aufgeloesten Ton und wirft hier; das Feld selbst steht fest
            float volume = sound instanceof AbstractSoundInstance && sound instanceof AbstractSoundInstanceAccessor accessor
                    ? accessor.shokimod$volume() : 1.0f;
            if (SoundMute.shouldCancel(id, volume)) cir.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
        } catch (RuntimeException ignored) {
            // Ein Ton, den wir nicht lesen koennen, wird einfach gespielt
        }
    }
}
