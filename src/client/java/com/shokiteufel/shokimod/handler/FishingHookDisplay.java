package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.data.FeatureGate;
import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.util.ScoreboardUtils;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.Items;

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
    private static final double SEARCH_RANGE = 2.0;
    /** Gelb und fett eine Zahl: der Name, wie Hypixel ihn schreibt, in Farbcodes (Farbe vor Formatierung) */
    private static final Pattern TIME = Pattern.compile("^(?:§r)?§e§l(\\d+(?:\\.\\d+)?)$");
    private static final Pattern ALERT = Pattern.compile("^(?:§.)*!!!$");

    /** Was ein Name sagt: ein Alarm, oder eine Restzeit in Sekunden */
    record Reading(boolean alert, double seconds, String legacy) {
    }

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

    private static void tick(Minecraft client) {
        tickClock++;
        if (!FeatureGate.hookDisplay()) {
            clear();
            return;
        }
        LocalPlayer player = client.player;
        if (player == null || client.level == null || !GameState.Server.isSkyblock()) {
            clear();
            return;
        }
        FishingHook hook = player.fishing;
        if (hook == null || hook.isRemoved() || !player.getMainHandItem().is(Items.FISHING_ROD)) {
            clear();
            return;
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
        if (best == null) {
            clear();
            return;
        }
        ModConfig.HookDisplayCategory cfg = ModConfig.INSTANCE.fishing.hookDisplay;
        shownId = best.getId();
        shownText = textFor(bestReading, cfg.customReelInTime, cfg.customAlertText);
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
