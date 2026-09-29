package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.data.ModConfig.ProfitCategory;
import com.shokiteufel.shokimod.scanner.ItemChanges;
import com.shokiteufel.shokimod.util.ItemNames;
import com.shokiteufel.shokimod.util.ItemValue;
import com.shokiteufel.shokimod.util.ItemValue.SellMode;
import com.shokiteufel.shokimod.util.SkyBlockItems;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Der Profit-Tracker: was seit dem Reset dazugekommen ist, und was es bringt.
 *
 * Gezaehlt wird, was {@link ItemChanges} meldet - also alles, was tatsaechlich ins
 * Inventar oder in einen Sack gewandert ist. Der Chat spielt keine Rolle mehr,
 * und damit auch nicht die Frage, ob Hypixel einen Fund fuer meldenswert haelt.
 *
 * Zwei Dinge lassen sich fuer jedes Item einzeln einstellen:
 *
 * <ul>
 *   <li><b>Ob es im Kasten steht.</b> Entweder steht alles drin ausser dem, was man
 *       ausblendet, oder nur das, was man angeklickt hat. Gezaehlt wird in beiden
 *       Faellen alles - wer die Auswahl spaeter aendert, findet die Funde von vorhin
 *       noch vor. Ausblenden loescht nichts.</li>
 *   <li><b>Wie es zu Geld wird.</b> Sofortverkauf, Verkaufsorder oder Haendler, je
 *       Item. Shards verkauft man sofort, einen Deep Sea Orb legt man in eine Order,
 *       und Enchanted Pumpkin nimmt ohnehin nur der NPC. Ohne eigene Wahl gilt die
 *       Voreinstellung des Kastens.</li>
 * </ul>
 *
 * Die Zeit laeuft wie beim Hunting Tracker: Pause nach Untaetigkeit, und die
 * Wartezeit seit dem letzten Fund wird dann wieder abgezogen.
 */
public final class ProfitTracker {

    private static final long SAVE_INTERVAL_MILLIS = 15_000L;

    /** Eine Zeile im Kasten: Item, Stueckzahl, Wert des ganzen Stapels */
    public record Row(String itemId, String name, int count, double value, boolean priced, SellMode mode) {
    }

    private static long lastTickMillis = 0L;
    private static long lastActivityMillis = 0L;
    /** Zeit seit dem letzten Fund, die noch nicht bestaetigt ist - faellt bei Pause weg */
    private static long unconfirmedMillis = 0L;
    private static boolean paused = true;
    private static boolean dirty = false;
    private static long lastSaveMillis = 0L;

    private ProfitTracker() {
    }

    public static ProfitCategory cfg() {
        return ModConfig.INSTANCE.profit;
    }

    public static boolean enabled() {
        return cfg().enabled;
    }

    public static void register() {
        ItemChanges.listen(ProfitTracker::onGains);
        ClientTickEvents.END_CLIENT_TICK.register(ProfitTracker::tick);
    }

    /** Ein Schwung Zugaenge aus dem Inventar oder aus einem Sack */
    private static void onGains(Map<String, Integer> gains) {
        if (!enabled()) return;

        boolean counted = false;
        for (Map.Entry<String, Integer> entry : gains.entrySet()) {
            String itemId = entry.getKey();
            int amount = entry.getValue();
            if (itemId == null || amount <= 0) continue;
            // Nur der erste Fund einer Ware kommt ins Log. Beim Minen faellt jede
            // Sekunde etwas an - Zeile fuer Zeile waere das Log nach einer Stunde
            // unlesbar, und mehr als "das hier wurde erkannt" sagt es nicht aus
            boolean first = !cfg().counts.containsKey(itemId);
            cfg().counts.merge(itemId, amount, Integer::sum);
            cfg().dayCounts.merge(itemId, amount, Integer::sum);
            cfg().totalCounts.merge(itemId, amount, Integer::sum);
            counted = true;
            // Die Namensfarbe wird nur im Augenblick des Fundes gesehen - festhalten,
            // solange sie da ist
            if (!cfg().colours.containsKey(itemId)) {
                int colour = ItemChanges.colourOf(itemId);
                if (colour != 0) cfg().colours.put(itemId, colour);
            }
            if (first) {
                ShokiMod.LOGGER.info("[Profit] first {} x{} via {}", itemId, amount, ItemChanges.lastSource());
            }
        }

        if (counted) {
            markActivity();
            dirty = true;
        }
    }

    private static void markActivity() {
        long now = System.currentTimeMillis();
        if (cfg().startedAt <= 0L) cfg().startedAt = now;
        if (cfg().totalStartedAt <= 0L) cfg().totalStartedAt = now;
        lastActivityMillis = now;
        unconfirmedMillis = 0L;
        paused = false;
    }

    private static void tick(Minecraft client) {
        long now = System.currentTimeMillis();
        long delta = lastTickMillis == 0L ? 0L : now - lastTickMillis;
        lastTickMillis = now;

        if (!enabled()) {
            paused = true;
            return;
        }

        // Die Preise sollen dastehen, sobald der Kasten sichtbar ist
        if (client.player != null && com.shokiteufel.shokimod.data.GameState.Server.isSkyblock()) {
            ItemValue.BAZAAR.prefetch();
            ItemNames.prefetch();
        }

        rollDay(now);

        // Bei 0 laeuft die Uhr durch: kein Anhalten bei Stille, und auch nicht, wenn das
        // Fenster im Hintergrund liegt. Wer das einstellt, will eine durchlaufende Uhr
        boolean neverPause = cfg().pauseAfterSeconds <= 0;
        long pauseAfter = Math.max(5, cfg().pauseAfterSeconds) * 1000L;
        boolean active = clockRuns() && lastActivityMillis > 0L
                && (neverPause || (now - lastActivityMillis <= pauseAfter && client.isWindowActive()));

        // Ohne Anhalten gibt es auch nichts zurueckzurechnen - sonst zieht ein spaeteres
        // Umstellen auf "mit Pause" die ganze durchgelaufene Zeit wieder ab
        if (neverPause) unconfirmedMillis = 0L;

        if (active) {
            if (delta > 0 && delta < 5_000L) {
                cfg().uptimeMillis += delta;
                cfg().dayUptimeMillis += delta;
                cfg().totalUptimeMillis += delta;
                unconfirmedMillis += delta;
            }
            paused = false;
        } else if (!paused) {
            // Die Wartezeit seit dem letzten Fund zaehlt in keinem der drei Zeitraeume
            cfg().uptimeMillis = Math.max(0L, cfg().uptimeMillis - unconfirmedMillis);
            cfg().dayUptimeMillis = Math.max(0L, cfg().dayUptimeMillis - unconfirmedMillis);
            cfg().totalUptimeMillis = Math.max(0L, cfg().totalUptimeMillis - unconfirmedMillis);
            unconfirmedMillis = 0L;
            paused = true;
            dirty = true;
        }

        if (dirty && now - lastSaveMillis >= SAVE_INTERVAL_MILLIS) {
            dirty = false;
            lastSaveMillis = now;
            ModConfig.INSTANCE.saveNow();
        }
    }

    /** Die Voreinstellung des Kastens */
    public static SellMode mode() {
        return cfg().priceMode == null ? SellMode.INSTANT_SELL : cfg().priceMode;
    }

    /** Die Verkaufsart dieses Items - eigene Wahl, sonst die Voreinstellung */
    public static SellMode modeOf(String itemId) {
        SellMode own = cfg().modes.get(itemId);
        return own == null ? mode() : own;
    }

    /** null setzt das Item auf die Voreinstellung zurueck */
    public static void setMode(String itemId, SellMode mode) {
        if (mode == null) cfg().modes.remove(itemId);
        else cfg().modes.put(itemId, mode);
        ModConfig.INSTANCE.saveNow();
    }

    /** Hat dieses Item eine eigene Wahl, oder folgt es dem Kasten? */
    public static boolean hasOwnMode(String itemId) {
        return cfg().modes.containsKey(itemId);
    }

    /**
     * Steht das Item im Kasten?
     *
     * Bei "Alles" zaehlt nur, was nicht ausgeblendet ist; bei "Nur Ausgewaehlte"
     * muss es angeklickt worden sein.
     */
    public static boolean shown(String itemId) {
        if (cfg().selection == ModConfig.ProfitSelection.PICKED) return cfg().picked.contains(itemId);
        return !cfg().hidden.contains(itemId);
    }

    /**
     * Von Hand nachbessern.
     *
     * Der Kasten zeigt, was gezaehlt wurde - nicht, was gefallen ist. Meistens ist das
     * dasselbe, aber wenn nicht, soll man es geradeziehen koennen, ohne alles
     * zurueckzusetzen. Unter null geht nichts; wer eine Ware auf null stellt, nimmt sie
     * aus der Liste.
     */
    public static void adjust(String itemId, int delta) {
        if (itemId == null || delta == 0) return;

        // In allen drei Zeitraeumen: Was hier falsch gezaehlt wurde, war auch im Tag und
        // im Gesamtstand falsch. Nur der angezeigte zu korrigieren hiesse, den Fehler in
        // den anderen beiden stehen zu lassen
        int updated = 0;
        for (Map<String, Integer> zaehler : List.of(cfg().counts, cfg().dayCounts, cfg().totalCounts)) {
            Integer vorher = zaehler.get(itemId);
            int neu = (vorher == null ? 0 : vorher) + delta;
            if (zaehler == activeCounts()) updated = neu;
            if (neu > 0) zaehler.put(itemId, neu);
            else zaehler.remove(itemId);
        }
        ModConfig.INSTANCE.saveNow();
        ShokiMod.LOGGER.info("[Profit] {} {} by hand -> {}", delta > 0 ? "+" + delta : delta,
                itemId, Math.max(0, updated));
    }

    /** Der Klick in der Liste: aus wird an, an wird aus - in beiden Betriebsarten */
    public static void toggle(String itemId) {
        if (itemId == null) return;
        if (cfg().selection == ModConfig.ProfitSelection.PICKED) {
            if (!cfg().picked.remove(itemId)) cfg().picked.add(itemId);
        } else {
            if (!cfg().hidden.remove(itemId)) cfg().hidden.add(itemId);
        }
        ModConfig.INSTANCE.saveNow();
    }

    /** Alle Kennungen, die seit dem Reset dazugekommen sind - auch die ausgeblendeten */
    public static List<String> seen() {
        return new ArrayList<>(activeCounts().keySet());
    }

    public static int countOf(String itemId) {
        Integer count = activeCounts().get(itemId);
        return count == null ? 0 : count;
    }

    /** Der Name, wie ihn das Spiel schreibt - sonst aus der Kennung gebildet */
    public static String nameOf(String itemId) {
        String name = ItemNames.displayName(itemId);
        return name == null || name.isBlank() ? SkyBlockItems.readableName(itemId) : name;
    }

    /**
     * Die Farbe, in der der Name steht - die Seltenheit des Items.
     *
     * Drei Quellen, in dieser Reihenfolge: was beim Fund am Gegenstand selbst zu
     * sehen war, was diese Sitzung gesehen hat, und sonst die Seltenheit aus
     * Hypixels Item-Liste. Wer in keiner steht, bleibt weiss.
     */
    public static int colourOf(String itemId) {
        Integer stored = cfg().colours.get(itemId);
        if (stored != null && stored != 0) return stored;

        int seen = ItemChanges.colourOf(itemId);
        if (seen != 0) return seen;

        int rarity = SkyBlockItems.rarityColour(ItemNames.tier(itemId));
        return rarity != 0 ? rarity : 0xFFFFFFFF;
    }

    /** Der selbst eingetragene Preis je Stueck, oder 0 */
    public static double customPrice(String itemId) {
        Double own = cfg().customPrices.get(itemId);
        return own == null || own <= 0 ? 0 : own;
    }

    /**
     * 0 oder weniger loescht den Eintrag wieder.
     *
     * Geschrieben wird hier nicht: Das Feld meldet jeden Tastendruck, und die Config
     * bei jedem Buchstaben auf die Platte zu legen waere ein Dutzend Schreibvorgaenge
     * je Zahl. Der Wert gilt sofort, gespeichert wird beim Schliessen des Fensters.
     */
    public static void setCustomPrice(String itemId, double coins) {
        if (itemId == null) return;
        if (coins > 0) cfg().customPrices.put(itemId, coins);
        else cfg().customPrices.remove(itemId);
    }

    /**
     * Was ein Stueck bringt, oder -1 wenn es dazu keine Zahl gibt.
     *
     * Der eigene Preis geht vor - aber nur, wenn einer eingetragen ist. Wer Custom
     * waehlt und das Feld leer laesst, sieht weiter den Marktpreis statt einer Null.
     */
    public static double unitPrice(String itemId) {
        if (modeOf(itemId) == SellMode.CUSTOM) {
            double own = customPrice(itemId);
            if (own > 0) return own;
        }
        return ItemValue.trackedUnitPrice(itemId, modeOf(itemId));
    }

    /**
     * Der Zaehlstand des Zeitraums, der gerade im Kasten steht.
     *
     * Gezaehlt wird immer in allen dreien; die Ansicht sucht nur aus, welcher davon
     * angezeigt, nachgebessert und zurueckgesetzt wird.
     */
    public static Map<String, Integer> activeCounts() {
        return switch (view()) {
            case DAY -> cfg().dayCounts;
            case TOTAL -> cfg().totalCounts;
            case SESSION -> cfg().counts;
        };
    }

    public static ModConfig.ProfitView view() {
        return cfg().view == null ? ModConfig.ProfitView.SESSION : cfg().view;
    }

    /** Der Klick auf die Ueberschrift: Lauf, Tag, alles, wieder Lauf */
    public static void cycleView() {
        ModConfig.ProfitView[] alle = ModConfig.ProfitView.values();
        cfg().view = alle[(view().ordinal() + 1) % alle.length];
        ModConfig.INSTANCE.saveNow();
    }

    /** Alle sichtbaren Zeilen, wertvollste zuerst */
    public static List<Row> rows() {
        List<Row> out = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : activeCounts().entrySet()) {
            String itemId = entry.getKey();
            int count = entry.getValue() == null ? 0 : entry.getValue();
            if (itemId == null || count <= 0 || !shown(itemId)) continue;

            double unit = unitPrice(itemId);
            out.add(new Row(itemId, nameOf(itemId), count, unit > 0 ? unit * count : 0,
                    unit > 0, modeOf(itemId)));
        }
        out.sort(Comparator.comparingDouble(Row::value).reversed().thenComparing(Row::name));
        return out;
    }

    /** Was alles Sichtbare zusammen wert ist */
    public static double total() {
        double sum = 0;
        for (Row row : rows()) sum += row.value();
        return sum;
    }

    public static double perHour() {
        long uptime = uptimeMillis();
        if (uptime < 1_000L) return 0;
        return total() / (uptime / 3_600_000d);
    }

    /**
     * Laeuft ueberhaupt eine Uhr?
     *
     * Wer weder die Zeit noch Profit/h anzeigt, zaehlt nur die Gegenstaende - dann gibt
     * es keine Uhr, die stehen bleiben koennte. Sie laeuft dann auch nicht mit: Eine
     * Zeit, die im Hintergrund weiterzaehlt, waere beim naechsten Einschalten um alle
     * Pausen zu lang.
     */
    public static boolean clockRuns() {
        return cfg().timerEnabled && (cfg().showTime || cfg().showPerHour);
    }

    /**
     * Steht die Uhr wegen einer Pause?
     *
     * Ohne Uhr ist die Antwort nein und nicht ja: Es pausiert nichts, es gibt nur nichts
     * zu zaehlen. Sonst stuende "(paused)" ueber einem Kasten, in dem keine Zeit steht.
     */
    public static boolean isPaused() {
        return clockRuns() && paused;
    }

    public static long uptimeMillis() {
        return switch (view()) {
            case DAY -> cfg().dayUptimeMillis;
            case TOTAL -> cfg().totalUptimeMillis;
            case SESSION -> cfg().uptimeMillis;
        };
    }

    /**
     * Ist ein neuer Tag angebrochen? Dann faengt der Tages-Zaehler wieder bei null an.
     *
     * Der Tag wechselt nicht um Mitternacht, sondern zur eingestellten Stunde - wer bis
     * drei Uhr nachts spielt, will das noch auf dem gestrigen Tag sehen. Lauf und
     * Gesamtstand bleiben davon unberuehrt.
     */
    private static void rollDay(long now) {
        long start = dayStart(now, cfg().dayResetHour);
        if (cfg().dayStartedAt >= start) return;

        boolean stand = !cfg().dayCounts.isEmpty() || cfg().dayUptimeMillis > 0L;
        cfg().dayCounts.clear();
        cfg().dayUptimeMillis = 0L;
        cfg().dayStartedAt = start;
        dirty = true;
        if (stand) ShokiMod.LOGGER.info("[Profit] a new day started, the day count is back to zero");
    }

    /**
     * Der Anfang des laufenden Tages, in der Zeit dieses Rechners.
     *
     * Liegt die Stunde heute noch vor einem, gehoert man noch zum gestrigen Tag.
     * Getrennt gehalten, damit die Rechnung ohne laufendes Spiel nachvollziehbar ist.
     */
    static long dayStart(long now, int hour) {
        int stunde = Math.clamp(hour, 0, 23);
        java.time.ZonedDateTime jetzt = java.time.Instant.ofEpochMilli(now)
                .atZone(java.time.ZoneId.systemDefault());
        java.time.ZonedDateTime grenze = jetzt.toLocalDate().atTime(stunde, 0).atZone(jetzt.getZone());
        if (grenze.isAfter(jetzt)) grenze = grenze.minusDays(1);
        return grenze.toInstant().toEpochMilli();
    }

    /**
     * Worauf gerade eine Bestaetigung aussteht - eine Kennung oder {@link #RESET_TOKEN}.
     *
     * Beides nimmt etwas weg, und beides passiert mit einem einzigen Klick im Kasten.
     * Eine Rueckfrage im Chat kostet einen zweiten Klick und rettet einen Lauf, der
     * sonst mit einem Danebengreifen weg waere.
     */
    private static String pending = null;
    private static long pendingAt = 0L;
    /** So lange gilt eine Frage. Danach ist sie keine Frage mehr, sondern ein Klick von gestern */
    private static final long CONFIRM_MILLIS = 30_000L;
    private static final String RESET_TOKEN = "*reset*";

    /**
     * Alle Waren, die der Kasten kennt - fuer die Vorschlagsliste des Befehls.
     *
     * Aus allen drei Zeitraeumen, denn wer eine Zahl geradezieht, meint die Ware und
     * nicht die Ansicht, in der sie gerade steht.
     */
    public static List<String> trackedNames() {
        java.util.LinkedHashSet<String> namen = new java.util.LinkedHashSet<>();
        for (Map<String, Integer> zaehler : List.of(cfg().counts, cfg().dayCounts, cfg().totalCounts)) {
            for (String id : zaehler.keySet()) namen.add(nameOf(id));
        }
        return new ArrayList<>(namen);
    }

    /**
     * Die Kennung zu einem eingetippten Namen, oder null.
     *
     * Drei Wege, in dieser Reihenfolge: die Item-Liste von Hypixel, ein Name aus dem
     * Kasten selbst - dort stehen auch Waren, die die Liste nicht fuehrt -, und zuletzt
     * die Kennung selbst, falls jemand ENCHANTED_RAW_FISH tippt.
     */
    static String idForName(String name) {
        if (name == null || name.isBlank()) return null;
        String gesucht = name.trim();

        List<String> ids = ItemNames.idsFor(gesucht);
        if (!ids.isEmpty()) return ids.get(0);

        for (Map<String, Integer> zaehler : List.of(cfg().counts, cfg().dayCounts, cfg().totalCounts)) {
            for (String id : zaehler.keySet()) {
                if (nameOf(id).equalsIgnoreCase(gesucht) || id.equalsIgnoreCase(gesucht)) return id;
            }
        }
        return null;
    }

    /** Was der Befehl mit der Zahl machen soll */
    public enum Change {
        SET, ADD, REMOVE
    }

    /**
     * Der Befehl /shoki profittracker.
     *
     * Gerechnet wird als Unterschied, nicht als neuer Stand: "set" holt den Abstand zum
     * gezeigten Zeitraum und legt ihn auf alle drei um. Sonst stuende nach einem
     * "set 500" im Gesamtstand ebenfalls 500, obwohl dort Tausende gezaehlt waren.
     */
    public static void applyChange(String name, Change change, int amount) {
        // Kein Blick auf den Spieler: Die Antwortzeile kuemmert sich selbst darum, und
        // die Rechnung soll auch ohne laufendes Spiel nachvollziehbar bleiben
        String itemId = idForName(name);
        if (itemId == null) {
            say(Component.literal("No item called ").withStyle(ChatFormatting.RED)
                    .append(Component.literal(name).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(".").withStyle(ChatFormatting.RED)));
            return;
        }

        int vorher = countOf(itemId);
        int delta = switch (change) {
            case SET -> amount - vorher;
            case ADD -> amount;
            case REMOVE -> -amount;
        };
        if (delta == 0) {
            say(Component.literal(nameOf(itemId)).withStyle(ChatFormatting.WHITE)
                    .append(Component.literal(" already stands at " + vorher + ".")
                            .withStyle(ChatFormatting.YELLOW)));
            return;
        }

        adjust(itemId, delta);
        say(Component.literal(nameOf(itemId)).withStyle(ChatFormatting.WHITE)
                .append(Component.literal(": " + vorher + " -> " + Math.max(0, vorher + delta)
                        + " (" + view() + ")").withStyle(ChatFormatting.YELLOW)));
    }

    /** Eine Zeile der Mod im Chat */
    private static void say(Component text) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) return;
        client.player.sendSystemMessage(Component.literal("[ShokiMod] ")
                .withStyle(ChatFormatting.DARK_AQUA).append(text));
    }

    /** Das [X] an einer Ware: erst fragen */
    public static void askRemove(String itemId) {
        if (itemId == null || countOf(itemId) <= 0) return;

        pending = itemId;
        pendingAt = System.currentTimeMillis();
        ask(Component.literal("Remove ").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(nameOf(itemId) + " x" + countOf(itemId))
                        .withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" from the tracker?").withStyle(ChatFormatting.YELLOW)));
    }

    /** Der Reset-Knopf: erst fragen */
    public static void askReset() {
        pending = RESET_TOKEN;
        pendingAt = System.currentTimeMillis();
        ask(Component.literal("Reset the ").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(view().toString()).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" count and its timer?").withStyle(ChatFormatting.YELLOW)));
    }

    /** Die Frage samt Knopf - der Knopf fuehrt den Befehl aus, der hier wieder ankommt */
    private static void ask(Component frage) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) return;

        client.player.sendSystemMessage(Component.literal("[ShokiMod] ")
                .withStyle(ChatFormatting.DARK_AQUA)
                .append(frage)
                .append(Component.literal(" [ Yes ]")
                        .withStyle(style -> style.withColor(ChatFormatting.GREEN)
                                .withBold(true)
                                .withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand("/shoki confirm"))
                                .withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(
                                        Component.literal("Click within 30 seconds")))))); 
    }

    /** Der Knopf aus der Frage, ueber /shoki confirm */
    public static void confirm() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) return;

        String was = pending;
        boolean abgelaufen = was != null && System.currentTimeMillis() - pendingAt > CONFIRM_MILLIS;
        pending = null;
        if (was == null || abgelaufen) {
            client.player.sendSystemMessage(Component.literal("[ShokiMod] ")
                    .withStyle(ChatFormatting.DARK_AQUA)
                    .append(Component.literal(abgelaufen
                                    ? "That question is too old - click the button again."
                                    : "Nothing to confirm.")
                            .withStyle(ChatFormatting.GRAY)));
            return;
        }

        if (RESET_TOKEN.equals(was)) {
            reset();
            client.player.sendSystemMessage(Component.literal("[ShokiMod] ")
                    .withStyle(ChatFormatting.DARK_AQUA)
                    .append(Component.literal(view() + " is back to zero.").withStyle(ChatFormatting.YELLOW)));
            return;
        }

        String name = nameOf(was);
        remove(was);
        client.player.sendSystemMessage(Component.literal("[ShokiMod] ")
                .withStyle(ChatFormatting.DARK_AQUA)
                .append(Component.literal(name).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" is out of the tracker.").withStyle(ChatFormatting.YELLOW)));
    }

    /**
     * Eine Ware ganz aus dem Kasten nehmen.
     *
     * Aus allen drei Zeitraeumen, genau wie das Nachbessern: Was hier nichts zu suchen
     * hat, hat es im Tag und im Gesamtstand auch nicht.
     */
    public static void remove(String itemId) {
        if (itemId == null) return;
        cfg().counts.remove(itemId);
        cfg().dayCounts.remove(itemId);
        cfg().totalCounts.remove(itemId);
        ModConfig.INSTANCE.saveNow();
        ShokiMod.LOGGER.info("[Profit] {} removed by hand", itemId);
    }

    /**
     * Der Reset-Knopf: Zaehlstand und Zeit auf null.
     *
     * Die Auswahl bleibt stehen. Wer sich seine Liste eingerichtet hat, will sie
     * nach dem naechsten Lauf wiederhaben und nicht neu anklicken.
     */
    public static void reset() {
        // Geleert wird, was man gerade ansieht - alles andere waere eine Ueberraschung
        switch (view()) {
            case DAY -> {
                cfg().dayCounts.clear();
                cfg().dayUptimeMillis = 0L;
            }
            case TOTAL -> {
                cfg().totalCounts.clear();
                cfg().totalUptimeMillis = 0L;
                cfg().totalStartedAt = 0L;
            }
            case SESSION -> {
                cfg().counts.clear();
                cfg().uptimeMillis = 0L;
                cfg().startedAt = 0L;
            }
        }
        lastActivityMillis = 0L;
        unconfirmedMillis = 0L;
        paused = true;
        dirty = false;
        ModConfig.INSTANCE.saveNow();
        ShokiMod.LOGGER.info("[Profit] {} reset", view());
    }
}
