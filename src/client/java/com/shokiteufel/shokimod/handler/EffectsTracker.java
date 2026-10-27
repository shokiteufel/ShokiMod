package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.data.ModConfig.EffectEntry;
import com.shokiteufel.shokimod.render.DropBanner;
import com.shokiteufel.shokimod.scanner.TabListScanner;
import com.shokiteufel.shokimod.util.AlertVolume;
import com.shokiteufel.shokimod.util.CustomSoundPlayer;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Merkt sich die Traenke und Effekte, die keine God Potion sind, und sagt Bescheid, wenn einer
 * ausgeht - nach dem Vorbild "Non-God Pot Effects" von SkyHanni (LGPL-3.0).
 *
 * Gelesen wird auf zwei Wegen, die SkyHanni auch nimmt: das Menue /effects ("Active Effects"),
 * in dem zu jedem Effekt "Remaining: 12:34:56" steht, und die Tab-Liste, die die Zeit
 * bei jedem Takt grob nachfuehrt. Jeder Effekt, der im Menue auftaucht, wird gemerkt;
 * was angezeigt wird und wann es Alarm gibt, stellt man je Effekt ein (/shoki effects). Die Liste dort
 * trennt, was die God Potion verleiht, von allem anderen (siehe groups).
 *
 * Gespeichert wird der Zeitpunkt des Endes, nicht die Restzeit: So laeuft die Uhr auch,
 * waehrend das Spiel zu ist, und ein Effekt, der inzwischen aus ist, steht auch so da.
 */
public final class EffectsTracker {

    private static final Pattern INVENTORY_TITLE = Pattern.compile("^(?:\\(\\d+/\\d+\\) )?Active Effects$");
    private static final Pattern REMAINING = Pattern.compile("^Remaining: (?<time>[\\d:]+)$");
    private static final Pattern TAB_EFFECT = Pattern.compile(
            " *(?<effect>[\\w\\-' ]+ (?<tier>[IVXLC]+)) ?(?:|[: ])+(?<time>[dhms0-9 ]+)");
    private static final Pattern DURATION_PART = Pattern.compile("(\\d+)([dhms])");
    private static final Pattern COLOUR_CODE = Pattern.compile("(?i)§[0-9A-FK-OR]");
    private static final String GOD_FILTER = "▶ God Potion Effects";
    private static final String CLEARED = "You cleared all of your active effects!";

    private static final long MENU_GAP_MILLIS = 500L;
    private static long lastMenuLook = 0L;
    private static long lastSecond = 0L;
    private static String lastOddTitle = "";

    /** Der Vorab-Alarm ("noch x Minuten") ist fuer diesen Effekt schon gekommen */
    private static final Set<String> earlyFired = new HashSet<>();
    /** Der Alarm "ist aus" ist schon gekommen */
    private static final Set<String> expiredFired = new HashSet<>();
    /** Wann zuletzt wiederholt wurde */
    private static final Map<String, Long> lastRepeat = new HashMap<>();

    private EffectsTracker() {
    }

    private static ModConfig.EffectsCategory cfg() {
        return ModConfig.INSTANCE.effects;
    }

    // ------------------------------------------------------------------ Zugriff

    /** Alle gemerkten Effekte, die nicht versteckt sind, nach Namen */
    public static List<EffectEntry> entries() {
        return sorted(false);
    }

    /** Die versteckten Effekte, nach Namen */
    public static List<EffectEntry> hiddenEntries() {
        return sorted(true);
    }

    private static List<EffectEntry> sorted(boolean hidden) {
        List<EffectEntry> out = new ArrayList<>();
        for (EffectEntry e : cfg().list.values()) {
            if (e.hidden == hidden) out.add(e);
        }
        out.sort(Comparator.comparing((EffectEntry e) -> e.name.toLowerCase(Locale.ROOT)));
        return out;
    }

    /** Eine Gruppe der Liste: ihr Name und die Effekte darunter */
    public record Group(String name, List<EffectEntry> entries) {
    }

    public static final String NON_GOD_GROUP = "Non-God Potion";
    public static final String GOD_GROUP = "God Potion";

    /**
     * Was die God Potion verleiht, mit Stufe - so steht es im Wiki (hypixelskyblock.minecraft.wiki/w/God_Potion)
     * und so heissen die Effekte auch im Menue und in der Tab-Liste. Kleingeschrieben, damit ein Vergleich
     * nicht an der Schreibweise haengt.
     */
    private static final Set<String> GOD_POTION_EFFECTS = Set.of(
            "critical iv", "regeneration ix", "strength viii", "agility iv", "night vision i", "absorption viii",
            "burning iv", "stun iv", "dodge iv", "experience iv", "mana viii", "speed viii", "water breathing vi",
            "alchemy xp boost iii", "combat xp boost iii", "enchanting xp boost iii", "farming xp boost iii",
            "fishing xp boost iii", "foraging xp boost iii", "mining xp boost iii", "rabbit vi", "resistance viii",
            "archery iv", "jump boost iv", "magic find iv", "pet luck iv", "spirit iv", "spelunker v",
            "adrenaline viii", "fire resistance i", "haste iv", "true resistance iv", "jerry candy");

    /**
     * Gehoert der Effekt zur God Potion? Name und Stufe muessen stimmen: Ein "Speed VI" aus einem
     * gewoehnlichen Trank bleibt bei den anderen, ein "Speed VIII" zaehlt zur God Potion.
     */
    public static boolean isGodPotion(String name) {
        return name != null && GOD_POTION_EFFECTS.contains(name.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT));
    }

    /** Die Effekte in zwei Gruppen: was die God Potion verleiht und alles andere. Eine leere Gruppe fehlt */
    public static List<Group> groups(boolean hidden) {
        List<EffectEntry> god = new ArrayList<>();
        List<EffectEntry> other = new ArrayList<>();
        for (EffectEntry e : sorted(hidden)) {
            (isGodPotion(e.name) ? god : other).add(e);
        }
        List<Group> out = new ArrayList<>();
        // Zuerst die Effekte, um die es hier geht: die Traenke ohne God Potion
        if (!other.isEmpty()) out.add(new Group(NON_GOD_GROUP, other));
        if (!god.isEmpty()) out.add(new Group(GOD_GROUP, god));
        return out;
    }

    /** Wie lange der Effekt noch laeuft, in Millisekunden - negativ, wenn er aus ist; null, wenn unbekannt */
    public static Long remaining(EffectEntry entry) {
        return entry.expiry <= 0L ? null : entry.expiry - System.currentTimeMillis();
    }

    public static void remove(EffectEntry entry) {
        cfg().list.remove(key(entry.name));
        forget(key(entry.name));
        ModConfig.INSTANCE.saveNow();
    }

    /**
     * Versteckt einen Effekt: weg aus Liste und Kasten, kein Alarm mehr. Der Eintrag bleibt, damit
     * /effects ihn nicht noch einmal als neu merkt - Entfernen allein brachte ihn beim naechsten
     * Oeffnen zurueck.
     */
    public static void hide(EffectEntry entry) {
        hideQuietly(entry);
        ModConfig.INSTANCE.saveNow();
    }

    private static void hideQuietly(EffectEntry entry) {
        entry.hidden = true;
        entry.hud = false;
        entry.alert = false;
        forget(key(entry.name));
    }

    /** Holt einen versteckten Effekt zurueck. HUD und Alarm bleiben aus, bis man sie wieder anhakt */
    public static void unhide(EffectEntry entry) {
        entry.hidden = false;
        ModConfig.INSTANCE.saveNow();
    }

    /**
     * Versteckt alles, was weder im Kasten steht noch Alarm gibt - also alles, was man nie
     * ausgesucht hat. Gedacht fuer den Fall, dass /effects eine lange Liste auf einmal gemerkt hat.
     *
     * @return wie viele versteckt wurden
     */
    public static int hideUnused() {
        int hidden = 0;
        for (EffectEntry e : cfg().list.values()) {
            if (e.hidden || e.hud || e.alert) continue;
            hideQuietly(e);
            hidden++;
        }
        if (hidden > 0) ModConfig.INSTANCE.saveNow();
        return hidden;
    }

    public static void toggleHud(EffectEntry entry) {
        entry.hud = !entry.hud;
        ModConfig.INSTANCE.saveNow();
    }

    public static void toggleAlert(EffectEntry entry) {
        entry.alert = !entry.alert;
        forget(key(entry.name));
        ModConfig.INSTANCE.saveNow();
    }

    private static void forget(String key) {
        earlyFired.remove(key);
        expiredFired.remove(key);
        lastRepeat.remove(key);
    }

    /** Um so viel muss das Ende nach hinten rutschen, damit es als neu aufgefrischt gilt */
    private static final long REFRESH_MILLIS = 15_000L;

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT).trim();
    }

    // ------------------------------------------------------------------ Takt

    public static void tick(Minecraft client) {
        if (client == null || client.player == null || client.level == null) return;
        long now = System.currentTimeMillis();

        if (client.gui.screen() instanceof AbstractContainerScreen<?> screen && now - lastMenuLook >= MENU_GAP_MILLIS) {
            lastMenuLook = now;
            // Das Menue wird auch bei ausgeschalteter Funktion gelesen: Wer /effects oeffnet,
            // will, dass es gemerkt wird, und schaltet den Rest danach ein
            readMenu(screen, now);
        }
        if (!cfg().enabled) return;

        if (now - lastSecond < 1000L) return;
        lastSecond = now;
        if (!GameState.Server.isSkyblock()) return;
        readTab(now);
        checkAlerts(now);
    }

    public static void onChatMessage(String plain) {
        if (plain == null || !cfg().enabled || !CLEARED.equals(plain.trim())) return;
        for (EffectEntry e : cfg().list.values()) e.expiry = 0L;
        earlyFired.clear();
        expiredFired.clear();
        lastRepeat.clear();
        ModConfig.INSTANCE.saveNow();
    }

    // ------------------------------------------------------------------ Lesen

    /** Das Menue /effects: jeder Effekt mit "Remaining: ..." wird gemerkt oder aufgefrischt */
    private static void readMenu(AbstractContainerScreen<?> screen, long now) {
        String title = clean(screen.getTitle().getString());
        if (!INVENTORY_TITLE.matcher(title).matches()) {
            if (title.toLowerCase(Locale.ROOT).contains("effect") && !title.equals(lastOddTitle)) {
                lastOddTitle = title;
                ShokiMod.LOGGER.info("[Effects] menu title not recognised: '{}'", title);
            }
            return;
        }

        // Zeigt der Filter die God Potion, sind das nicht die gesuchten Effekte
        for (Slot slot : screen.getMenu().slots) {
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;
            if (clean(stack.getHoverName().getString()).equals("Filter")
                    && lore(stack).stream().anyMatch(GOD_FILTER::equals)) {
                return;
            }
        }

        boolean changed = false;
        for (Slot slot : screen.getMenu().slots) {
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;
            Long ms = remainingOf(lore(stack));
            if (ms == null) continue;

            String name = clean(stack.getHoverName().getString());
            if (name.isEmpty()) continue;
            EffectEntry entry = cfg().list.get(key(name));
            if (entry == null) {
                entry = new EffectEntry();
                entry.name = name;
                cfg().list.put(key(name), entry);
                ShokiMod.LOGGER.info("[Effects] remembered {} ({})", name, isGodPotion(name) ? GOD_GROUP : NON_GOD_GROUP);
                changed = true;
            }
            long end = now + ms;
            if (Math.abs(entry.expiry - end) > 2000L) {
                entry.expiry = end;
                forget(key(name));
                changed = true;
            }
        }
        if (changed) ModConfig.INSTANCE.saveNow();
    }

    /** Die Tab-Liste: nur bei Effekten, die schon gemerkt sind. Die Zeit dort ist auf die letzte Einheit gekuerzt */
    private static void readTab(long now) {
        boolean changed = false;
        for (String line : TabListScanner.lastLines()) {
            Matcher m = TAB_EFFECT.matcher(line);
            if (!m.matches()) continue;
            EffectEntry entry = cfg().list.get(key(m.group("effect")));
            if (entry == null) continue;

            long shown = durationOf(m.group("time"));
            if (shown <= 0L) continue;
            long unit = shown % 60_000L != 0L ? 1000L
                    : shown % 3_600_000L != 0L ? 60_000L
                    : shown % 86_400_000L != 0L ? 3_600_000L : 86_400_000L;
            long upper = shown + unit;
            long estimate = entry.expiry <= 0L ? 0L : entry.expiry - now;
            // Die Schaetzung bleibt, solange sie in dem Bereich liegt, den die Anzeige zulaesst
            long fixed = estimate < shown ? shown : estimate >= upper ? upper - 1000L : estimate;
            if (Math.abs(fixed - estimate) > 1000L || entry.expiry <= 0L) {
                long before = entry.expiry;
                entry.expiry = now + fixed;
                // Die Alarme gelten wieder, wenn der Effekt wirklich aufgefrischt wurde. Eine Anzeige,
                // die stehen bleibt (Smoldering Polarization zaehlt nicht immer herunter), schiebt
                // das Ende nur Sekunde um Sekunde nach hinten - das ist kein neuer Effekt, und
                // jedes Vergessen liess den Alarm jede Sekunde neu ausloesen
                if (before <= 0L || entry.expiry - before > REFRESH_MILLIS) forget(key(entry.name));
                changed = true;
            }
        }
        if (changed) ModConfig.INSTANCE.saveNow();
    }

    /** "Remaining: 105:01:34" aus der Beschreibung, in Millisekunden */
    static Long remainingOf(List<String> lore) {
        for (String line : lore) {
            Matcher m = REMAINING.matcher(line.trim());
            if (!m.matches()) continue;
            String[] parts = m.group("time").split(":");
            long total = 0L;
            try {
                if (parts.length == 4) {
                    // Tage:Stunden:Minuten:Sekunden
                    total = ((Long.parseLong(parts[0]) * 24L + Long.parseLong(parts[1])) * 60L
                            + Long.parseLong(parts[2])) * 60L + Long.parseLong(parts[3]);
                } else {
                    for (String part : parts) total = total * 60L + Long.parseLong(part);
                }
            } catch (NumberFormatException e) {
                return null;
            }
            return total * 1000L;
        }
        return null;
    }

    /** "1d 2h 3m 4s" in Millisekunden, 0 wenn nichts erkannt wird */
    static long durationOf(String text) {
        long total = 0L;
        Matcher m = DURATION_PART.matcher(text == null ? "" : text);
        while (m.find()) {
            long n = Long.parseLong(m.group(1));
            total += switch (m.group(2)) {
                case "d" -> n * 86_400_000L;
                case "h" -> n * 3_600_000L;
                case "m" -> n * 60_000L;
                default -> n * 1000L;
            };
        }
        return total;
    }

    private static List<String> lore(ItemStack stack) {
        List<String> out = new ArrayList<>();
        net.minecraft.world.item.component.ItemLore lore =
                stack.get(net.minecraft.core.component.DataComponents.LORE);
        if (lore == null) return out;
        for (Component line : lore.lines()) out.add(clean(line.getString()));
        return out;
    }

    private static String clean(String text) {
        return text == null ? "" : COLOUR_CODE.matcher(text).replaceAll("").trim();
    }

    // ------------------------------------------------------------------ Alarm

    private static void checkAlerts(long now) {
        ModConfig.EffectsCategory c = cfg();
        long early = c.alertMinutes * 60_000L;
        for (EffectEntry e : c.list.values()) {
            if (e.hidden || !e.alert || e.expiry <= 0L) continue;
            String k = key(e.name);
            long left = e.expiry - now;

            if (left > 0L) {
                expiredFired.remove(k);
                lastRepeat.remove(k);
                if (early > 0L && left <= early) {
                    if (earlyFired.add(k)) {
                        fire(e, "Effect running out", minutes(left) + " left", 0xFFAA00,
                                " runs out in " + minutesLong(left) + ".");
                    }
                } else {
                    earlyFired.remove(k);
                }
                continue;
            }

            if (!c.alertOnExpire && !c.repeatExpired) continue;
            if (expiredFired.add(k)) {
                lastRepeat.put(k, now);
                if (c.alertOnExpire) fire(e, "Effect ran out", "renew it", 0xFF5555, " has run out.");
            } else if (c.repeatExpired
                    && now - lastRepeat.getOrDefault(k, 0L) >= Math.max(5, c.repeatSeconds) * 1000L) {
                lastRepeat.put(k, now);
                fire(e, "Effect is still off", "renew it", 0xFF5555, " is still off.");
            }
            earlyFired.add(k);
        }
    }

    /** "5 minutes", "1 minute" - fuer die Chatzeile */
    private static String minutesLong(long millis) {
        long minutes = Math.max(1L, Math.round(millis / 60_000.0));
        return minutes + (minutes == 1L ? " minute" : " minutes");
    }

    private static String minutes(long millis) {
        long minutes = Math.max(1L, Math.round(millis / 60_000.0));
        return minutes + " min";
    }

    private static void fire(EffectEntry entry, String headline, String detail, int rgb, String chatText) {
        ModConfig.EffectsCategory c = cfg();
        if (c.banner) {
            DropBanner.show(ModConfig.INSTANCE.chat.banner.designOrDefault(c.bannerDesign),
                    headline, entry.name, detail, rgb, ItemStack.EMPTY);
        }
        if (c.chatLine) {
            Minecraft client = Minecraft.getInstance();
            if (client != null && client.player != null) {
                client.player.sendSystemMessage(Component.literal("[ShokiMod] ")
                        .withStyle(ChatFormatting.DARK_AQUA)
                        .append(Component.literal(entry.name).withStyle(ChatFormatting.WHITE))
                        .append(Component.literal(chatText).withStyle(ChatFormatting.YELLOW)));
            }
        }
        String ton = c.sound;
        if (ton != null && !ton.isBlank()) {
            CustomSoundPlayer.play(ton, AlertVolume.factor(), EffectsTracker.class);
        }
        ShokiMod.LOGGER.info("[Effects] {}: {} - {}", entry.name, headline, detail);
    }

    /** Der Knopf in den Einstellungen: einmal vorfuehren */
    public static void test() {
        EffectEntry probe = new EffectEntry();
        probe.name = "Spelunker V";
        fire(probe, "Effect running out", "5 min left", 0xFFAA00, " runs out in 5 minutes.");
    }
}
