package com.shokiteufel.shokimod.scanner;

import com.shokiteufel.shokimod.data.FeatureGate;
import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.util.ItemNames;
import com.shokiteufel.shokimod.util.SkyBlockItems;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Was ins Inventar kommt - ohne auf den Chat angewiesen zu sein.
 *
 * Hypixel meldet laengst nicht jeden Fund. "RARE DROP!" steht im Chat, ein
 * gewoehnlicher Deep Sea Orb nicht, und was direkt in einen Sack faellt, sieht der
 * Chat nur als Sammelzeile. Wer Funde am Chat abliest, verpasst deshalb einen Teil
 * davon - genau das ist beim Fund-Alarm zu sehen.
 *
 * Hier wird stattdessen jeden Tick nachgezaehlt, was im Inventar liegt, und mit dem
 * Stand davor verglichen. Was mehr geworden ist, ist dazugekommen - egal ob es eine
 * Meldung dazu gab. Denselben Weg geht Skysofts Profit Tracker
 * (SkyBlockInventoryChanges, LGPL-3.0, Akinsoft).
 *
 * Drei Dinge machen den Unterschied zwischen "zaehlt Funde" und "zaehlt Unsinn":
 *
 * <ul>
 *   <li><b>Ruhe nach einem Wechsel.</b> Nach Warp, Serverwechsel oder Tod kommt das
 *       Inventar Stueck fuer Stueck an. Wer da vergleicht, sieht sein halbes Inventar
 *       als Fund. Deshalb wird nach jedem Wechsel gewartet, bis sich eine Sekunde
 *       lang nichts mehr regt, und dann neu angesetzt - ohne zu melden.</li>
 *   <li><b>Kein offenes Fenster.</b> Was durch ein Fenster hereinkommt, ist kein Fund:
 *       Basar, Auktionshaus, Truhe, Sack, NPC, Craft. Solange ein Behaelter offen ist,
 *       wird nicht verglichen, und danach wird neu angesetzt.</li>
 *   <li><b>Verrechnen statt doppelt zaehlen.</b> Wer etwas wegwirft und wieder
 *       aufhebt, hat nichts gewonnen. Abgaenge bleiben zehn Sekunden stehen und
 *       werden gegen spaetere Zugaenge derselben Ware aufgerechnet.</li>
 * </ul>
 */
public final class ItemChanges {

    /** Wie lange nichts passieren muss, bis der Stand nach einem Wechsel wieder gilt (Ticks) */
    private static final int SETTLE_TICKS = 20;
    /** So lange wird ein Abgang gegen einen spaeteren Zugang derselben Ware aufgerechnet */
    private static final long OFFSET_MILLIS = 10_000L;
    /** Der Platz des SkyBlock-Menues in der Schnellleiste - sein Inhalt wechselt staendig */
    private static final int MENU_SLOT = 8;

    private static final Pattern COLOUR_CODE = Pattern.compile("§.");
    private static final Pattern LEADING_SYMBOLS = Pattern.compile("^[^\\p{L}\\p{N}]+");
    private static final Pattern TRAILING_SYMBOLS = Pattern.compile("[^\\p{L}\\p{N}]+$");
    /** "+38 Enchanted Helix Log (Enchanted Foraging Sack)" */
    private static final Pattern SACK_LINE = Pattern.compile("^([+-])\\s*([\\d,.]+)\\s+(.+?)\\s*\\(");
    private static final String SACK_MARKER = "[Sacks]";

    private static final List<Consumer<Map<String, Integer>>> listeners = new ArrayList<>(2);

    /** Der Stand des letzten Vergleichs: Kennung -> Stueckzahl im Inventar */
    private static Map<String, Integer> previousCounts = null;
    /** Fingerabdruck der Plaetze. Aendert er sich nicht, muss auch nicht gezaehlt werden */
    private static int previousSignature = 0;
    private static String previousContext = null;
    /** Nach einem Wechsel: warten, bis Ruhe ist, und dann neu ansetzen statt zu melden */
    private static boolean settling = true;
    private static int stableTicks = 0;
    /** Abgaenge der letzten Sekunden, gegen die spaetere Zugaenge verrechnet werden */
    private static final Map<String, Integer> pendingLosses = new HashMap<>();
    private static long lossesAt = 0L;

    private ItemChanges() {
    }

    /** Wer mitzaehlen will, meldet sich hier an. Die Karte enthaelt nur Zugaenge */
    public static void listen(Consumer<Map<String, Integer>> listener) {
        if (listener != null) listeners.add(listener);
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(ItemChanges::tick);
    }

    /** Steht schon ein Vergleichsstand, oder wird noch gewartet? */
    public static boolean ready() {
        return !settling && previousCounts != null;
    }

    private static void tick(Minecraft client) {
        if (!FeatureGate.itemChanges()) {
            reset();
            return;
        }
        if (client.player == null || client.level == null || !GameState.Server.isSkyblock()) {
            reset();
            return;
        }

        // Ein offener Behaelter ist der Weg, auf dem Gekauftes, Ausgelagertes und
        // Gecraftetes hereinkommt. Nichts davon ist ein Fund
        if (client.screen instanceof AbstractContainerScreen<?>) {
            settling = true;
            stableTicks = 0;
            return;
        }

        String context = context(client);
        if (!context.equals(previousContext)) {
            previousContext = context;
            settling = true;
            stableTicks = 0;
            previousCounts = null;
        }

        // Der billige Teil zuerst: hat sich ueberhaupt ein Platz geruehrt? Zwanzigmal je
        // Sekunde lautet die Antwort fast immer nein, und dann faellt alles Weitere weg
        int signature = signature(client);
        if (signature == previousSignature) {
            stableTicks++;
            if (settling && stableTicks >= SETTLE_TICKS) {
                previousCounts = counts(client);
                settling = false;
            }
            return;
        }

        previousSignature = signature;
        stableTicks = 0;

        Map<String, Integer> current = counts(client);
        if (settling || previousCounts == null) {
            previousCounts = current;
            return;
        }

        Map<String, Integer> gains = diff(previousCounts, current);
        previousCounts = current;
        if (!gains.isEmpty()) dispatch(gains);
    }

    /**
     * Wo wir sind, und wer wir sind.
     *
     * Aendert sich hier etwas, ist das Inventar gerade unterwegs: Serverwechsel,
     * Warp, neues Profil. Die Server-ID steht in der Tab-Liste und wechselt bei
     * jedem Sprung.
     */
    private static String context(Minecraft client) {
        return GameState.Server.id + "|" + client.player.getUUID() + "|"
                + System.identityHashCode(client.level);
    }

    /**
     * Ein Fingerabdruck aller Plaetze.
     *
     * Der teure Teil ist das Auslesen der Kennungen; der billige ist dieser Vergleich.
     * Er laeuft ohne eine einzige neue Liste - was hier jeden Tick angelegt wuerde,
     * muesste auch jeden Tick wieder weggeraeumt werden.
     *
     * Ohne Ruestung, ohne das SkyBlock-Menue (dessen Platz sich staendig aendert),
     * aber mit dem, was am Mauszeiger haengt: das liegt in keinem Platz und waere
     * sonst ein Abgang.
     */
    private static int signature(Minecraft client) {
        List<ItemStack> items = client.player.getInventory().getNonEquipmentItems();
        int hash = 1;
        for (int slot = 0; slot < items.size(); slot++) {
            if (slot == MENU_SLOT) continue;
            ItemStack stack = items.get(slot);
            if (stack == null || stack.isEmpty()) continue;
            hash = hash * 31 + ItemStack.hashItemAndComponents(stack) * 31 + stack.getCount();
        }
        ItemStack carried = client.player.containerMenu.getCarried();
        if (carried != null && !carried.isEmpty()) {
            hash = hash * 31 + ItemStack.hashItemAndComponents(carried) * 31 + carried.getCount();
        }
        return hash;
    }

    /** Wie viel von welcher Ware der Spieler gerade bei sich hat */
    private static Map<String, Integer> counts(Minecraft client) {
        Map<String, Integer> out = new LinkedHashMap<>();
        List<ItemStack> items = client.player.getInventory().getNonEquipmentItems();
        for (int slot = 0; slot < items.size(); slot++) {
            if (slot == MENU_SLOT) continue;
            add(out, items.get(slot));
        }
        add(out, client.player.containerMenu.getCarried());
        return out;
    }

    private static void add(Map<String, Integer> counts, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        String id = SkyBlockItems.idOf(stack);
        if (id == null) return;
        counts.merge(id, stack.getCount(), Integer::sum);
    }

    /** Zugaenge zwischen zwei Staenden, verrechnet mit den Abgaengen der letzten Sekunden */
    private static Map<String, Integer> diff(Map<String, Integer> before, Map<String, Integer> after) {
        expireLosses();
        Map<String, Integer> gains = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : after.entrySet()) {
            int delta = entry.getValue() - before.getOrDefault(entry.getKey(), 0);
            if (delta <= 0) continue;

            int offset = pendingLosses.getOrDefault(entry.getKey(), 0);
            if (offset > 0) {
                int used = Math.min(offset, delta);
                delta -= used;
                if (offset - used <= 0) pendingLosses.remove(entry.getKey());
                else pendingLosses.put(entry.getKey(), offset - used);
            }
            if (delta > 0) gains.put(entry.getKey(), delta);
        }
        // Was verschwunden ist, bleibt kurz vorgemerkt: wer wegwirft und wieder aufhebt,
        // hat nichts gefunden
        for (Map.Entry<String, Integer> entry : before.entrySet()) {
            int delta = entry.getValue() - after.getOrDefault(entry.getKey(), 0);
            if (delta > 0) {
                pendingLosses.merge(entry.getKey(), delta, Integer::sum);
                lossesAt = System.currentTimeMillis();
            }
        }
        return gains;
    }

    private static void expireLosses() {
        if (pendingLosses.isEmpty()) return;
        if (System.currentTimeMillis() - lossesAt > OFFSET_MILLIS) pendingLosses.clear();
    }

    /**
     * Die Sammelzeile der Saecke.
     *
     * Was direkt in einen Sack faellt, kommt nie im Inventar an. Die Zeile
     * "[Sacks] +38 items." traegt am Mauszeiger die Aufstellung - dieselbe Quelle,
     * aus der auch der Collection-Tracker liest.
     */
    public static void onChatMessage(Component message, String plain) {
        if (message == null || plain == null || !plain.contains(SACK_MARKER)) return;
        if (!FeatureGate.itemChanges() || !GameState.Server.isSkyblock()) return;
        // Wer von Hand einlagert, hat den Sack offen - das ist kein Fund, sondern ein Umzug
        Minecraft client = Minecraft.getInstance();
        if (client.screen instanceof AbstractContainerScreen<?>) return;

        List<String> hover = new ArrayList<>();
        collectHoverText(message, hover);
        if (hover.isEmpty()) return;

        Map<String, Integer> gains = new LinkedHashMap<>();
        boolean adding = false;
        for (String line : hover) {
            String clean = COLOUR_CODE.matcher(line).replaceAll("").trim();
            String lower = clean.toLowerCase(Locale.ROOT);
            if (lower.startsWith("added items")) {
                adding = true;
                continue;
            }
            if (lower.startsWith("removed items")) {
                adding = false;
                continue;
            }
            if (!adding) continue;

            Matcher matcher = SACK_LINE.matcher(clean);
            if (!matcher.find()) continue;
            if ("-".equals(matcher.group(1))) continue;
            int amount = number(matcher.group(2));
            if (amount <= 0) continue;

            String name = TRAILING_SYMBOLS.matcher(LEADING_SYMBOLS.matcher(matcher.group(3))
                    .replaceAll("")).replaceAll("").trim();
            if (name.isEmpty()) continue;
            List<String> ids = ItemNames.idsFor(name);
            if (ids.isEmpty()) continue;
            gains.merge(ids.get(0), amount, Integer::sum);
        }

        if (!gains.isEmpty()) dispatch(gains);
    }

    private static int number(String text) {
        try {
            return Integer.parseInt(text.replace(",", "").replace(".", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static void collectHoverText(Component component, List<String> out) {
        HoverEvent hover = component.getStyle().getHoverEvent();
        if (hover instanceof HoverEvent.ShowText showText) {
            for (String line : showText.value().getString().split("\n")) out.add(line);
        }
        for (Component sibling : component.getSiblings()) collectHoverText(sibling, out);
    }

    private static void dispatch(Map<String, Integer> gains) {
        for (int i = 0; i < listeners.size(); i++) {
            listeners.get(i).accept(gains);
        }
    }

    /** Beim Verlassen von SkyBlock faellt der Vergleichsstand weg, nicht die Zaehlung */
    public static void reset() {
        previousCounts = null;
        previousContext = null;
        previousSignature = 0;
        settling = true;
        stableTicks = 0;
        pendingLosses.clear();
    }
}
