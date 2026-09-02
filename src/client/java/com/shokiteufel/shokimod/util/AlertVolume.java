package com.shokiteufel.shokimod.util;

import com.shokiteufel.shokimod.data.ModConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

/**
 * Ein Regler fuer alle Alarmtoene.
 *
 * Chatregeln, seltene Funde und die Contest-Warnung spielen ihre Toene alle ueber
 * diesen Faktor. Wer nachts leiser spielen will, dreht an einer Stelle, nicht an
 * jeder Regel einzeln. Die Lautstaerke einer einzelnen Regel bleibt erhalten und
 * wird nur skaliert.
 */
public final class AlertVolume {

    private AlertVolume() {
    }

    /** Der eingestellte Faktor, sicher zwischen 0 und 1 */
    public static float factor() {
        float value = ModConfig.INSTANCE.chat.alertVolume;
        if (Float.isNaN(value)) return 1.0f;
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    /** Eine Regel-Lautstaerke durch den Regler geschickt */
    public static float scale(float volume) {
        return Math.max(0.0f, Math.min(1.0f, volume)) * factor();
    }

    /**
     * Der Testknopf: spielt einen eigenen Alarmton in der eingestellten Lautstaerke.
     *
     * Genommen wird die erste Datei, die bei den seltenen Funden oder dem Contest
     * eingetragen ist - so hoert man, was man spaeter auch hoeren wird. Ist keine
     * eingetragen, tut es Minecrafts Notenblock.
     */
    public static void test() {
        ModConfig config = ModConfig.INSTANCE;
        String[] candidates = {
                config.chat.rareLoot.tier1Sound,
                config.chat.rareLoot.tier2Sound,
                config.chat.rareLoot.tier3Sound,
                config.safari.contestWarningSound,
        };
        for (String file : candidates) {
            if (file != null && !file.isBlank() && CustomSoundPlayer.isSupported(file)) {
                CustomSoundPlayer.play(file, factor(), AlertVolume.class);
                return;
            }
        }

        Minecraft client = Minecraft.getInstance();
        client.getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, factor()));
    }
}
