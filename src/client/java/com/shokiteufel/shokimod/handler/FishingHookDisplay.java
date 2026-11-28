package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.data.FeatureGate;
import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.util.FishingLog;
import com.shokiteufel.shokimod.util.ScoreboardUtils;
import com.shokiteufel.shokimod.util.SkyBlockItems;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
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
        if (sound == null || !FishingLog.enabled()) return;
        // Ein Werkzeug zum Fehlersuchen darf das Spiel nie selbst zum Absturz bringen
        try {
            FishingHookProbe.sound(sound);
        } catch (RuntimeException | LinkageError e) {
            // Dann fehlt eben die Zeile
        }
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
        FishingHook hook = player.fishing;
        if (hook == null || hook.isRemoved()) {
            clear();
            if (log) FishingHookProbe.state(hook == null ? "no-hook" : "hook-removed", hook == null ? "no hook out (player.fishing is null)" : "the hook is removed");
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
            FishingHookProbe.state("tracking:" + hook.getId() + ":" + medium + ":" + held, String.format(Locale.ROOT,
                    "tracking hook id=%d, %s, holding %s, the hook is %d ticks old", hook.getId(), medium, held, hook.tickCount));
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
            if (log) FishingHookProbe.scene(client, hook, null, null, null);
            return;
        }
        ModConfig.HookDisplayCategory cfg = ModConfig.INSTANCE.fishing.hookDisplay;
        shownId = best.getId();
        shownText = textFor(bestReading, cfg.customReelInTime, cfg.customAlertText);
        if (log) FishingHookProbe.scene(client, hook, best, bestReading, shownText);
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
