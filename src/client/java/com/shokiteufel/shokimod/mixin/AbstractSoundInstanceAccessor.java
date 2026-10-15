package com.shokiteufel.shokimod.mixin;

import net.minecraft.client.resources.sounds.AbstractSoundInstance;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Tonhoehe und Lautstaerke eines Tons, wie der Erzeuger sie eingestellt hat.
 *
 * {@code getPitch()} und {@code getVolume()} rechnen mit dem noch nicht aufgeloesten Ton und
 * werfen am Eingang von {@code SoundManager.play}; die Felder selbst stehen schon fest. Die
 * Werte verraten den Erzeuger: Wer Toene baut, stellt sie meist auf eigene Werte.
 */
@Mixin(AbstractSoundInstance.class)
public interface AbstractSoundInstanceAccessor {

    @Accessor("pitch")
    float shokimod$pitch();

    @Accessor("volume")
    float shokimod$volume();
}
