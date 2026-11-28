package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.data.FeatureGate;
import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.mixin.AbstractSoundInstanceAccessor;
import com.shokiteufel.shokimod.util.FishingLog;
import com.shokiteufel.shokimod.util.ModSound;
import com.shokiteufel.shokimod.util.ScoreboardUtils;
import com.shokiteufel.shokimod.util.SkyBlockItems;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Die Zeit ueber dem eigenen Angelhaken, gross auf dem Bildschirm.
 *
 * Herkunft der Idee: SkyHanni (LGPL-2.1, "Fishing Hook Display"). Hypixel setzt einen Ruestungsstaender ueber den Haken, dessen Name von etwa 3.0
 * herunterzaehlt und bei "!!!" steht, sobald man einholen kann. Die Regel von dort: Der Name ist entweder gelb und fett eine Zahl ("3.0")
 * oder ein "!!!" in beliebiger Farbe; jeder andere Ruestungsstaender neben dem Haken (Schadenszahlen, Namen von Mobs) ist es nicht. Die
 * Umsetzung ist eigene Arbeit: Java, eine Suche je Tick in der Naehe des eigenen Hakens statt Ereignisse fuer jede Namensaenderung, und
 * der Wortlaut der Einstellungen ist ein eigener.
 *
 * Gesucht wird nur, solange die Funktion an ist, man in SkyBlock steht, einen Haken im Wasser (oder in der Lava) hat und die Angel haelt.
 */
public final class FishingHookDisplay {

    /** Wie weit um den Haken nach dem Ruestungsstaender gesucht wird - Hypixel setzt ihn unmittelbar darueber */
    static final double SEARCH_RANGE = 2.0;
    /** Gelb und fett eine Zahl: der Name, wie Hypixel ihn schreibt, in Farbcodes (Farbe vor Formatierung) */
    private static final Pattern TIME = Pattern.compile("^(?:§r)?§e§l(\\d+(?:\\.\\d+)?)$");
    private static final Pattern ALERT = Pattern.compile("^(?:§.)*!!!$");

    /** Was ein Name sagt: ein Alarm, oder eine Restzeit in Sekunden */
    record Reading(boolean alert, double seconds, String legacy) {
    }

    /** Hypixels Biss-Ton: das Pling eines Notenblocks (belegt im Fishing log vom 28.11.: Tonhoehe 1.00, Lautstaerke 1.00, rund 30 ms vor dem "!!!") */
    static final String BITE_NAMESPACE = "minecraft";
    static final String BITE_PATH = "block.note_block.pling";
    /** So alt muss der Haken sein, bevor ein Pling als Biss gilt (der Wurf selbst klingt auch) */
    private static final int BITE_MIN_AGE_TICKS = 10;

    /** Der Haken, bei dem der Biss-Ton gehoert wurde, oder -1 */
    private static int soundAlertHook = -1;
    private static int shownId = -1;
    private static String shownText = null;
    /** Zaehlt mit jedem Tick, damit der Kasten ebenso oft neu gebaut wird und der Alarm nicht eine Viertelsekunde zu spaet kommt */
    private static long tickClock = 0L;

    private FishingHookDisplay() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(FishingHookDisplay::tick);
    }

    /** Die Uhr des letzten Ticks - nach ihr baut der Kasten sich neu (SafariHud, tickgebunden) */
    public static long tickTime() {
        return tickClock;
    }

    /**
     * Liest einen Namen: eine Restzeit, ein Alarm - oder null, wenn der Name nichts davon ist.
     *
     * @param legacy der Name in Farbcodes, wie ihn {@link ScoreboardUtils#toLegacyString} liefert
     */
    static Reading read(String legacy) {
        if (legacy == null) return null;
        Matcher time = TIME.matcher(legacy);
        if (time.matches()) {
            try {
                return new Reading(false, Double.parseDouble(time.group(1)), legacy);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        if (ALERT.matcher(legacy).matches()) return new Reading(true, 0.0, legacy);
        return null;
    }

    /**
     * Was auf dem Bildschirm steht: bei einem Alarm - oder wenn die Restzeit die eingestellte Zeit erreicht hat - der eigene Alarmtext
     * (mit & als Farbzeichen), sonst Hypixels Text unveraendert.
     */
    static String textFor(Reading reading, float reelInTime, String customAlert) {
        if (reading == null) return null;
        String custom = customAlert == null ? "" : customAlert.replace('&', '§');
        if (reading.alert() || reading.seconds() <= reelInTime) return custom.isEmpty() ? reading.legacy() : custom;
        return reading.legacy();
    }

    private static void clear() {
        shownId = -1;
        shownText = null;
    }

    /**
     * Ein Ton ist gestartet (vom Mixin an {@code SoundManager.play}, jeder Ton laeuft hier vorbei): fuer das Fishing log. Ohne das Protokoll nur ein Vergleich.
     */
    public static void onSound(SoundInstance sound) {
        if (sound == null) return;
        boolean log = FishingLog.enabled();
        boolean alert = ModConfig.INSTANCE.fishing.hookDisplay.soundAlert && FeatureGate.hookDisplay();
        if (!log && !alert) return;
        // Ein Werkzeug darf das Spiel nie selbst zum Absturz bringen
        try {
            // Ein Ton, den die Mod selbst spielt (ModSound), zaehlt nie als Biss - auch wenn er ein Pling mit denselben Werten ist
            boolean own = ModSound.active();
            if (alert && !own) heardBite(sound, log);
            if (log) FishingHookProbe.sound(sound, own);
        } catch (RuntimeException | LinkageError e) {
            // Dann fehlt eben der Alarm oder die Zeile
        }
    }

    /** Ist das Hypixels Biss-Ton? Das Pling eines Notenblocks mit Tonhoehe 1.00 und Lautstaerke 1.00; eine Tonfolge mit anderen Werten ist etwas anderes */
    static boolean isBiteSound(String namespace, String path, float pitch, float volume) {
        return BITE_NAMESPACE.equals(namespace) && BITE_PATH.equals(path) && Math.abs(pitch - 1.0f) < 0.01f && Math.abs(volume - 1.0f) < 0.01f;
    }

    /**
     * Was im Overlay steht, wenn der Biss-Ton gehoert wurde: der Alarm, auch wenn der Ruestungsstaender keinen (oder noch die Zeit) zeigt. Ohne Ton bleibt
     * es, wie es ist.
     */
    static Reading withSoundAlert(Reading reading, boolean heard) {
        if (!heard) return reading;
        if (reading != null && reading.alert()) return reading;
        return new Reading(true, 0.0, "§c§l!!!");
    }

    /** Der Ton ist gestartet: Ist es der Biss-Ton bei meinem eigenen Haken, merkt sich der naechste Tick den Alarm */
    private static void heardBite(SoundInstance sound, boolean log) {
        Identifier id = sound.getIdentifier();
        // Zuerst das Billige: Der Pfad. Jeder Ton im Spiel laeuft hier vorbei
        if (id == null || !BITE_PATH.equals(id.getPath())) return;
        if (!(sound instanceof AbstractSoundInstance) || !(sound instanceof AbstractSoundInstanceAccessor accessor)) return;
        float pitch = accessor.shokimod$pitch();
        float volume = accessor.shokimod$volume();
        if (!isBiteSound(id.getNamespace(), id.getPath(), pitch, volume)) return;

        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null || !GameState.Server.isSkyblock()) return;
        FishingHook hook = OwnHook.of(player);
        if (hook == null || hook.isRemoved() || !player.getMainHandItem().is(Items.FISHING_ROD)) return;
        // Der Wurf selbst klingt auch - ein Biss kommt erst nach einer Weile
        if (hook.tickCount < BITE_MIN_AGE_TICKS) return;
        soundAlertHook = hook.getId();
        if (log) FishingLog.line("BITE", "bite sound heard for hook id=" + hook.getId() + " (" + hook.tickCount + " ticks old) - the alert is shown from now on");
    }

    /** Wie die Angel in der Hand heisst, fuers Fishing log: die SkyBlock-Kennung, sonst der Name */
    private static String describeItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "nothing";
        String id = SkyBlockItems.idOf(stack);
        return id != null ? id : stack.getHoverName().getString();
    }

    private static void tick(Minecraft client) {
        tickClock++;
        boolean log = FishingLog.enabled();
        if (log) {
            // Das Protokoll schreibt auch bei ausgeschalteter Hook display mit: es merkt sich den Haken und die Zeit fuer die Toene
            FishingHookProbe.watch(client);
        } else {
            FishingLog.forget();
        }
        if (!FeatureGate.hookDisplay()) {
            clear();
            if (log) {
                FishingHookProbe.state("off", "Hook display is off (its own switch: " + ModConfig.INSTANCE.fishing.hookDisplay.enabled
                        + ", the 26.3 switch: " + FeatureGate.update263() + ") - nothing is read");
            }
            return;
        }
        LocalPlayer player = client.player;
        if (player == null || client.level == null || !GameState.Server.isSkyblock()) {
            clear();
            if (log) FishingHookProbe.state("not-skyblock", "no player, no world or not in SkyBlock (map: " + GameState.Server.map + ")");
            return;
        }
        // Das Feld player.fishing allein genuegt nicht: Wirft man gleich nach dem Einholen aus, loescht das Entfernen des alten Hakens den Zeiger auf den neuen
        // (Fishing log vom 28.11.). OwnHook sucht dann den eigenen Haken in der Welt
        FishingHook hook = OwnHook.of(player);
        if (hook == null || hook.isRemoved()) {
            clear();
            if (log) FishingHookProbe.state("no-hook", "no hook out (player.fishing is null and no hook of mine is in the world)");
            return;
        }
        if (!player.getMainHandItem().is(Items.FISHING_ROD)) {
            clear();
            if (log) {
                String held = describeItem(player.getMainHandItem());
                FishingHookProbe.state("no-rod:" + held, "a hook is out (id=" + hook.getId() + ") but the main hand is not a fishing rod: " + held
                        + " (off hand: " + describeItem(player.getOffhandItem()) + ") - the display is cleared");
            }
            return;
        }
        if (log) {
            String medium = hook.isInLava() ? "in lava" : hook.isInWater() ? "in water" : "in the air";
            String held = describeItem(player.getMainHandItem());
            // Vom Feld nicht genannt, nur durch die Suche gefunden: das waere bis 2.6.18 ein nicht erkannter Haken gewesen
            boolean bySearch = OwnHook.foundBySearch(player, hook);
            FishingHookProbe.state("tracking:" + hook.getId() + ":" + medium + ":" + held + (bySearch ? ":search" : ""), String.format(Locale.ROOT,
                    "tracking hook id=%d, %s, holding %s, the hook is %d ticks old%s", hook.getId(), medium, held, hook.tickCount,
                    bySearch ? " - FOUND BY SEARCH, player.fishing does not name it" : ""));
        }

        ArmorStand best = null;
        Reading bestReading = null;
        double bestDistance = Double.MAX_VALUE;
        for (ArmorStand stand : client.level.getEntitiesOfClass(ArmorStand.class, hook.getBoundingBox().inflate(SEARCH_RANGE))) {
            Component name = stand.getCustomName();
            if (name == null) continue;
            Reading reading = read(ScoreboardUtils.toLegacyString(name));
            if (reading == null) continue;
            double distance = stand.distanceToSqr(hook);
            if (distance < bestDistance) {
                best = stand;
                bestReading = reading;
                bestDistance = distance;
            }
        }
        ModConfig.HookDisplayCategory cfg = ModConfig.INSTANCE.fishing.hookDisplay;
        // Der Biss-Ton bei diesem Haken macht den Alarm, auch wenn der Ruestungsstaender nichts Lesbares zeigt
        Reading effective = withSoundAlert(bestReading, cfg.soundAlert && soundAlertHook == hook.getId());
        if (effective == null) {
            clear();
            if (log) FishingHookProbe.scene(client, hook, null, null, null);
            return;
        }
        shownId = best == null ? -1 : best.getId();
        shownText = textFor(effective, cfg.customReelInTime, cfg.customAlertText);
        if (log) FishingHookProbe.scene(client, hook, best, effective, shownText);
    }

    /** Der Text, der gerade auf dem Bildschirm stehen soll - oder null, wenn der Haken nichts zu sagen hat */
    public static String shown() {
        return shownText;
    }

    /**
     * Soll dieser Ruestungsstaender nicht gezeichnet werden? Der Text darueber steht schon auf dem Bildschirm. Vom Einsprung in
     * {@code EntityRenderer.shouldRender} gerufen - fuer jeden Ruestungsstaender in jedem Bild, also nur ein Vergleich.
     */
    public static boolean hides(ArmorStand stand) {
        return shownText != null && stand.getId() == shownId && ModConfig.INSTANCE.fishing.hookDisplay.hideArmorStand;
    }
}
