package com.shokiteufel.shokimod.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;

/**
 * Jeder Ton, den die Mod ueber Minecrafts Tonsystem spielt, geht hier durch - und ist damit als "von ShokiMod" gekennzeichnet.
 *
 * Anlass (28.11.2026): Das Fishing log zeigte, dass Hypixels Biss-Ton ein Notenblock-Pling ist ({@code minecraft:block.note_block.pling}, Tonhoehe 1.00, Lautstaerke
 * 1.00). Die Mod spielt selbst Notenblock-Plings (Cocoon-Warnungen mit genau diesen Werten, Erinnerungen mit anderen), und alle Toene - auch die der Mod - laufen durch
 * dieselbe Stelle ({@code SoundManager.play}). Eine Funktion, die Toene erkennt oder leiser stellt, muss die eigenen auseinanderhalten koennen: Der Alarm aus dem
 * Biss-Ton darf nicht von einem eigenen Pling ausgeloest werden, und die Lautstaerke-Einstellung darf die eigenen Toene nie veraendern ("die Sounds, die von
 * ShokiMod kommen, sollen weiterhin abgespielt werden").
 *
 * Die Kennzeichnung ist eine Marke am Faden, die nur waehrend des Abspielens gilt - der Ton wird in demselben Aufruf gestartet, also sieht der Einsprung in
 * {@code SoundManager.play} sie ({@link #active()}). Die eigenen Audiodateien ({@link CustomSoundPlayer}) laufen ueber Javas Audio und nicht durch Minecrafts Tonsystem;
 * sie brauchen keine Marke.
 *
 * Wer einen neuen Ton spielen will, nimmt {@link #play} oder {@link #playUi}; ein Test ({@code ModSoundSourceTest}) schlaegt an, wenn irgendwo im Quelltext direkt
 * gespielt wird.
 */
public final class ModSound {

    private static final ThreadLocal<int[]> DEPTH = ThreadLocal.withInitial(() -> new int[1]);

    private ModSound() {
    }

    /** Spielt gerade die Mod einen Ton (auf diesem Faden)? Fuer den Einsprung in {@code SoundManager.play} */
    public static boolean active() {
        return DEPTH.get()[0] > 0;
    }

    /** Fuehrt die Aktion aus, waehrend alles, was dabei klingt, als Ton der Mod gilt. Verschachtelt, und die Marke faellt auch bei einem Fehler wieder */
    public static void run(Runnable action) {
        int[] depth = DEPTH.get();
        depth[0]++;
        try {
            action.run();
        } finally {
            depth[0]--;
        }
    }

    /** Ein Ton beim Spieler, wie {@code player.playSound(event, volume, pitch)} */
    public static void play(LocalPlayer player, SoundEvent event, float volume, float pitch) {
        run(() -> player.playSound(event, volume, pitch));
    }

    /** Ein Ton der Oberflaeche (ohne Ort), wie {@code SimpleSoundInstance.forUI(event, pitch, volume)} */
    public static void playUi(SoundEvent event, float pitch, float volume) {
        run(() -> Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(event, pitch, volume)));
    }
}
