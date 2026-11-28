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
 * Andere Toene leiser stellen - zwei voneinander unabhaengige Einstellungen, jede mit eigenem Anteil:
 * <ul>
 *   <li><b>Fishing > Hook display > Quiet other sounds</b>: solange der eigene Haken draussen ist (Angel in der Haupthand).</li>
 *   <li><b>Fishing > Frozen Blaze > Quiet other sounds</b> (seit 2.7.2): solange die Blazing Aura wirkt ({@link FrozenBlaze#auraActive()}) - mit dem ganzen Set, die 30
 *       Sekunden nach der letzten Bewegung, auch zwischen den Wuerfen und ohne Haken. Wunsch des Users (28.11.): "in der Frozen-Blaze-Kategorie und unabhaengig von der
 *       anderen".</li>
 * </ul>
 * Treffen beide zu, gilt der staerkere Anteil.
 *
 * <p>Wunsch des Users (28.11.): "genau diesen Sound lauter machen um 100 % und alles andere um 50-100 % leiser". Minecraft begrenzt die Lautstaerke eines Tons auf
 * 100 %; der Biss-Ton kommt schon mit 1.00 (Fishing log), also bleibt er, wie er ist, und alles andere wird um den eingestellten Anteil leiser. Das Ergebnis ist
 * dasselbe: der Biss-Ton ist der laute.
 *
 * <p>Nie angefasst werden: der Biss-Ton selbst, jeder Ton, den die Mod selbst spielt ({@link ModSound}; Vorgabe des Users: "die Sounds, die von ShokiMod kommen,
 * sollen weiterhin abgespielt werden"), und jeder Ton, den die Stummschaltung ohnehin verschluckt ({@link SoundMute#wouldCancel}; sie erkennt das Geschenk an der
 * unveraenderten Lautstaerke). Die eigenen Audiodateien der Mod laufen nicht durch Minecrafts Tonsystem und sind davon nicht betroffen.
 *
 * <p>Der Einsprung ist {@code SoundEngine.play} ({@code HookSoundMixin}; jeder Ton genau einmal, auch die zeitverzoegerten weit entfernten), vor dem Abspielen; die
 * Lautstaerke wird im Feld des Tons gesetzt.
 */
public final class HookSounds {

    private HookSounds() {
    }

    /**
     * Wird dieser Ton leiser gestellt? Rein - jede Bedingung ein Ausschluss.
     *
     * @param featureOn   ob irgendeine der beiden Einstellungen jetzt zutrifft (Haken draussen, oder die Aura wirkt)
     * @param own         ob die Mod den Ton selbst spielt
     * @param quietTime   ob jetzt eine Zeit ist, in der leiser gestellt wird (siehe {@link #percentFor})
     * @param bite        ob es der Biss-Ton ist
     * @param wouldCancel ob die Stummschaltung ihn ohnehin verschluckt
     */
    static boolean shouldQuiet(boolean featureOn, boolean own, boolean quietTime, boolean bite, boolean wouldCancel) {
        return featureOn && quietTime && !own && !bite && !wouldCancel;
    }

    /**
     * Um wie viel Prozent jetzt leiser gestellt wird - der staerkere Anteil der Einstellungen, die gerade zutreffen; eine Zahl unter null, wenn keine zutrifft.
     *
     * @param hookQuiet   ob "Quiet other sounds" bei der Hook display an ist UND der eigene Haken draussen ist
     * @param hookPercent sein Anteil
     * @param auraQuiet   ob "Quiet other sounds" bei Frozen Blaze an ist UND die Blazing Aura wirkt
     * @param auraPercent sein Anteil
     */
    static float percentFor(boolean hookQuiet, float hookPercent, boolean auraQuiet, float auraPercent) {
        float best = -1.0f;
        if (hookQuiet && !Float.isNaN(hookPercent)) best = Math.max(best, clamp(hookPercent));
        if (auraQuiet && !Float.isNaN(auraPercent)) best = Math.max(best, clamp(auraPercent));
        return best;
    }

    private static float clamp(float percent) {
        return Math.max(0.0f, Math.min(100.0f, percent));
    }

    /**
     * Die Lautstaerke, um den Anteil leiser.
     *
     * @param percent um wie viel Prozent leiser (0 aendert nichts, 100 macht stumm); ausserhalb davon wird auf 0 bis 100 zurechtgerueckt, ein Unsinn ({@code NaN})
     *                aendert nichts
     */
    static float quieter(float volume, float percent) {
        if (Float.isNaN(percent)) return volume;
        return volume * (1.0f - clamp(percent) / 100.0f);
    }

    /** Vom Mixin an {@code SoundEngine.play}: laeuft bei jedem Ton, also zuerst der billigste Ausschluss (beide Einstellungen aus) */
    public static void onPlay(SoundInstance sound) {
        boolean hookFeature = FeatureGate.hookSounds();
        boolean auraFeature = FeatureGate.auraSounds();
        if (sound == null || (!hookFeature && !auraFeature)) return;
        // Ein Werkzeug darf das Spiel nie selbst zum Absturz bringen: Im Zweifel bleibt der Ton, wie er ist
        try {
            if (ModSound.active()) return;
            if (!(sound instanceof AbstractSoundInstance) || !(sound instanceof AbstractSoundInstanceAccessor accessor)) return;
            Minecraft client = Minecraft.getInstance();
            LocalPlayer player = client.player;
            if (player == null || !GameState.Server.isSkyblock()) return;

            boolean hookOut = false;
            if (hookFeature) {
                // OwnHook: auch ein Haken, den player.fishing nach schnellem Wiederauswerfen nicht mehr nennt
                FishingHook hook = OwnHook.of(player);
                hookOut = hook != null && !hook.isRemoved() && player.getMainHandItem().is(Items.FISHING_ROD);
            }
            boolean auraOn = auraFeature && FrozenBlaze.auraActive();
            float percent = percentFor(hookOut, ModConfig.INSTANCE.fishing.hookDisplay.quietOthersBy, auraOn, ModConfig.INSTANCE.fishing.frozenBlaze.quietSoundsBy);
            if (percent < 0.0f) return;

            Identifier id = sound.getIdentifier();
            float volume = accessor.shokimod$volume();
            float pitch = accessor.shokimod$pitch();
            boolean bite = id != null && FishingHookDisplay.isBiteSound(id.getNamespace(), id.getPath(), pitch, volume);
            boolean mute = SoundMute.wouldCancel(id, volume);
            if (!shouldQuiet(true, false, true, bite, mute)) return;
            accessor.shokimod$setVolume(quieter(volume, percent));
        } catch (RuntimeException | LinkageError e) {
            // Dann bleibt der Ton, wie er ist
        }
    }
}
