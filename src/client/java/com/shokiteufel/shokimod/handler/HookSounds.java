package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.data.FeatureGate;
import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.mixin.AbstractSoundInstanceAccessor;
import com.shokiteufel.shokimod.util.ModSound;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.Items;

/**
 * Fishing > Hook display > Quiet other sounds: Solange der eigene Haken draussen ist, werden alle Toene leiser gestellt - bis auf den Biss-Ton, damit er heraussticht.
 * Mit "Also during the Blazing Aura" (Wunsch des Users, 28.11.: "bei Frozen Blaze mit rein, dass sie aus sind, solange der Blazing-Aura-Effekt aktiv ist") gilt das auch
 * dann, wenn man das ganze Frozen-Blaze-Set traegt und die Aura wirkt ({@link FrozenBlaze#auraActive()}) - ohne Haken, durchgehend; der eingestellte Anteil
 * ist derselbe, 100 schaltet die anderen Toene ganz aus.
 *
 * Wunsch des Users (28.11.): "genau diesen Sound lauter machen um 100 % und alles andere um 50-100 % leiser". Minecraft begrenzt die Lautstaerke eines Tons auf 100 %;
 * der Biss-Ton kommt schon mit 1.00 (Fishing log), also bleibt er, wie er ist, und alles andere wird um den eingestellten Anteil leiser. Das Ergebnis ist dasselbe:
 * der Biss-Ton ist der laute.
 *
 * <p>Nie angefasst werden: der Biss-Ton selbst, jeder Ton, den die Mod selbst spielt ({@link ModSound}; Vorgabe des Users: "die Sounds, die von ShokiMod kommen,
 * sollen weiterhin abgespielt werden"), und jeder Ton, den die Stummschaltung ohnehin verschluckt ({@link SoundMute#wouldCancel}; sie erkennt das Geschenk an der
 * unveraenderten Lautstaerke). Die eigenen Audiodateien der Mod laufen nicht durch Minecrafts Tonsystem und sind davon nicht betroffen.
 *
 * <p>Der Einsprung ist {@code SoundManager.play} (alle Toene, auch die des Servers), vor dem Abspielen; die Lautstaerke wird im Feld des Tons gesetzt.
 */
public final class HookSounds {

    private HookSounds() {
    }

    /**
     * Wird dieser Ton leiser gestellt? Rein - jede Bedingung ein Ausschluss.
     *
     * @param featureOn   ob die Einstellung an ist
     * @param own         ob die Mod den Ton selbst spielt
     * @param hookOut     ob der eigene Haken draussen ist (Angel in der Haupthand, SkyBlock)
     * @param bite        ob es der Biss-Ton ist
     * @param wouldCancel ob die Stummschaltung ihn ohnehin verschluckt
     */
    static boolean shouldQuiet(boolean featureOn, boolean own, boolean hookOut, boolean bite, boolean wouldCancel) {
        return featureOn && hookOut && !own && !bite && !wouldCancel;
    }

    /**
     * Ist jetzt die Zeit, in der leiser gestellt wird? Solange der eigene Haken draussen ist, und - mit "Also during the Blazing Aura" - auch solange die Blazing Aura
     * wirkt (Frozen Blaze, die 30 Sekunden nach der letzten Bewegung), selbst zwischen den Wuerfen und ohne Haken.
     *
     * @param hookOut    ob der eigene Haken draussen ist (Angel in der Haupthand)
     * @param auraOption ob "Also during the Blazing Aura" an ist
     * @param auraActive ob die Blazing Aura gerade wirkt
     */
    static boolean quietTime(boolean hookOut, boolean auraOption, boolean auraActive) {
        return hookOut || (auraOption && auraActive);
    }

    /**
     * Die Lautstaerke, um den Anteil leiser.
     *
     * @param percent um wie viel Prozent leiser (0 aendert nichts, 100 macht stumm); ausserhalb davon wird auf 0 bis 100 zurechtgerueckt, ein Unsinn ({@code NaN})
     *                aendert nichts
     */
    static float quieter(float volume, float percent) {
        if (Float.isNaN(percent)) return volume;
        float by = Math.max(0.0f, Math.min(100.0f, percent));
        return volume * (1.0f - by / 100.0f);
    }

    /** Vom Mixin an {@code SoundManager.play}: laeuft bei jedem Ton, also zuerst der billigste Ausschluss (die Einstellung) */
    public static void onPlay(SoundInstance sound) {
        if (sound == null || !FeatureGate.hookSounds()) return;
        // Ein Werkzeug darf das Spiel nie selbst zum Absturz bringen: Im Zweifel bleibt der Ton, wie er ist
        try {
            if (ModSound.active()) return;
            if (!(sound instanceof AbstractSoundInstance) || !(sound instanceof AbstractSoundInstanceAccessor accessor)) return;
            Minecraft client = Minecraft.getInstance();
            LocalPlayer player = client.player;
            if (player == null || !GameState.Server.isSkyblock()) return;
            // OwnHook: auch ein Haken, den player.fishing nach schnellem Wiederauswerfen nicht mehr nennt
            FishingHook hook = OwnHook.of(player);
            boolean hookOut = hook != null && !hook.isRemoved() && player.getMainHandItem().is(Items.FISHING_ROD);

            Identifier id = sound.getIdentifier();
            float volume = accessor.shokimod$volume();
            float pitch = accessor.shokimod$pitch();
            boolean bite = id != null && FishingHookDisplay.isBiteSound(id.getNamespace(), id.getPath(), pitch, volume);
            boolean mute = SoundMute.wouldCancel(id, volume);
            boolean auraOption = ModConfig.INSTANCE.fishing.hookDisplay.quietWhileAura;
            if (!shouldQuiet(true, false, quietTime(hookOut, auraOption, auraOption && FrozenBlaze.auraActive()), bite, mute)) return;
            accessor.shokimod$setVolume(quieter(volume, ModConfig.INSTANCE.fishing.hookDisplay.quietOthersBy));
        } catch (RuntimeException | LinkageError e) {
            // Dann bleibt der Ton, wie er ist
        }
    }
}
