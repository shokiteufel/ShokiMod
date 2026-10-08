package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.data.ModConfig.ProfitCategory;
import com.shokiteufel.shokimod.scanner.ItemChanges;
import com.shokiteufel.shokimod.util.CollectionData;
import com.shokiteufel.shokimod.util.ItemNames;
import com.shokiteufel.shokimod.util.ItemValue;
import com.shokiteufel.shokimod.util.ItemValue.SellMode;
import com.shokiteufel.shokimod.util.PetLevels;
import com.shokiteufel.shokimod.util.SkyBlockItems;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    /**
     * Eine Zeile des Kastens.
     *
     * Die Menge ist eine ganze Zahl, auch bei hochgerechneten Waren: Gecraftet wird in
     * ganzen Stuecken, und was nicht fuer eines reicht, bleibt liegen. 400 Bones sind
     * zwei Enchanted Bones und achtzig Bones - keine zweieinhalb.
     */
    public record Row(String itemId, String name, long count, double value, boolean priced, SellMode mode) {
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
            playSound(itemId);
        }

        if (counted) {
            markActivity();
            dirty = true;
        }
    }

    /** So lange ohne Fund gilt der Tag noch als laufend. Fest, nicht einstellbar */
    private static final long DAY_PAUSE_MILLIS = 30_000L;
    private static boolean dayPaused = true;

    private static void markActivity() {
        long now = System.currentTimeMillis();
        if (cfg().startedAt <= 0L) cfg().startedAt = now;
        if (cfg().totalStartedAt <= 0L) cfg().totalStartedAt = now;
        lastActivityMillis = now;
        unconfirmedMillis = 0L;
        paused = false;
        dayPaused = false;
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
        retryPendingPets(now);

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
                cfg().totalUptimeMillis += delta;
                unconfirmedMillis += delta;
            }
            paused = false;
        } else if (!paused) {
            // Die Wartezeit seit dem letzten Fund zaehlt nicht mit
            cfg().uptimeMillis = Math.max(0L, cfg().uptimeMillis - unconfirmedMillis);
            cfg().totalUptimeMillis = Math.max(0L, cfg().totalUptimeMillis - unconfirmedMillis);
            unconfirmedMillis = 0L;
            paused = true;
            dirty = true;
        }

        // Die Uhr des Tages laeuft immer, auch wenn keine Zeit im Kasten steht: Sonst
        // stuende in /shoki dayprofit bei jedem Tag "0m", und die Frage "wie lange war
        // ich dran" ist genau die, fuer die das Fenster da ist.
        //
        // Und sie laeuft anders als die des Laufs: Was nach einem Fund dazukommt, bleibt
        // stehen. Jeder Fund schenkt dem Tag bis zu dreissig Sekunden, und liegen zwei
        // Funde dichter beieinander, laeuft die Uhr durch. Der Lauf rechnet die Wartezeit
        // zurueck, damit Profit je Stunde nicht verwaessert - der Tag will aber wissen,
        // wie lange man dran war, und ein einzelner Fund war eben keine Null.
        boolean dayActive = lastActivityMillis > 0L
                && now - lastActivityMillis <= DAY_PAUSE_MILLIS && client.isWindowActive();
        if (dayActive) {
            if (delta > 0 && delta < 5_000L) cfg().dayUptimeMillis += delta;
            dayPaused = false;
        } else if (!dayPaused) {
            dayPaused = true;
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
    /**
     * Ist das ein Koeder?
     *
     * Alle enden auf _BAIT. Nur bei ihnen steht der Kasten im Minus: Verbrauchte Koeder
     * sind eine Ausgabe, und sie gehoert in die Rechnung - wer 600 verfischt und 200
     * gefangen hat, hat 400 Koeder bezahlt.
     */
    public static boolean isBait(String itemId) {
        return itemId != null && itemId.endsWith("_BAIT");
    }

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
            // Ein Koeder darf unter null: Er ist Geld, das ausgegeben wurde, und der
            // Kasten zeigt dann das Minus, statt es zu verschweigen. Alles andere gibt
            // es nicht in negativer Menge - da waere es ein Zaehlfehler
            if (neu > 0 || (neu < 0 && isBait(itemId))) zaehler.put(itemId, neu);
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
        Matcher maxed = MAXED_PET.matcher(itemId == null ? "" : itemId);
        if (maxed.matches()) {
            // Ohne die Seltenheit im Namen: Die steht in der Farbe der Zeile, so wie im
            // Spiel auch - "[Lvl 100] Ender Dragon" in Gold sagt beides auf einmal
            String pet = ItemNames.shortName(maxed.group("pet"));
            int klammer = pet.lastIndexOf(" (");
            if (klammer > 0 && pet.endsWith(")")) pet = pet.substring(0, klammer);
            return "[Lvl " + maxed.group("level") + "] " + pet;
        }
        return ItemNames.shortName(itemId);
    }

    /**
     * Ein Pet auf Hoechststufe: "ENDER_DRAGON;4+100".
     *
     * Das Plus ist keine Erfindung fuer diesen Kasten - so schreibt es Feesh auch, und
     * zwei Mods, die dieselbe Ware gleich benennen, ersparen dem naechsten Leser eine
     * Uebersetzung. Vor dem Plus steht die gewoehnliche Pet-Kennung, dahinter die Stufe.
     */
    private static final Pattern MAXED_PET = Pattern.compile(
            "^(?<pet>[A-Z_]+;(?<tier>[0-5]))\\+(?<level>100|200)$");

    /**
     * "Your Ender Dragon leveled up to level 100!"
     *
     * Ein Pet, das die Hoechststufe erreicht, ist der Ertrag vieler Stunden - und im
     * Kasten stand davon bisher nichts, weil nie etwas ins Inventar fiel. Gezaehlt wird
     * es mit dem Preis, den ein fertiges Exemplar gerade kostet; die Stufen darunter
     * zaehlen nicht, sonst stuende dasselbe Pet hundertmal da.
     *
     * Die Seltenheit steht in der Farbe des Namens - anders ist sie aus der Zeile nicht
     * zu holen, und ohne sie waere ein legendaerer Drache so viel wert wie ein epischer.
     */
    private static final Pattern LEVEL_UP = Pattern.compile(
            "^Your (?<pet>.+?) leveled up to level (?<level>\\d+)!$");

    public static void onChatMessage(String formatted, String plain) {
        // Kein Blick auf den Schalter des Kastens: Die Einblendung bei einem fertigen
        // Pet haengt nicht am Tracker - wer ihn aus hat, will sie trotzdem sehen
        if (plain == null) return;

        Matcher matcher = LEVEL_UP.matcher(plain.trim());
        if (!matcher.matches()) return;

        int level;
        try {
            level = Integer.parseInt(matcher.group("level"));
        } catch (NumberFormatException e) {
            return;
        }
        // Unter hundert ist kein Pet fertig, und darueber gibt es nur die drei Drachen
        if (level != 100 && level != 200) return;

        String name = matcher.group("pet").trim();
        String petId = name.toUpperCase(Locale.ROOT).replace(' ', '_').replaceAll("[^A-Z_]", "");
        notePetLevel(petId, rarityOf(String.valueOf(tierFromColour(formatted, name))), level);
    }

    /**
     * Ein Pet hat eine Stufe erreicht - zaehlt es?
     *
     * Nur auf seiner eigenen Hoechststufe. Die liegt bei hundert, ausser bei Golden,
     * Jade und Rose Dragon: Die gehen bis zweihundert, und ein Golden Dragon auf hundert
     * ist nicht fertig, sondern halb fertig. Welche Stufe die letzte ist, steht in
     * denselben Zahlen, aus denen auch der Pet-Gewinn rechnet - eine Liste der drei
     * Drachen im Code waere beim vierten falsch.
     *
     * Sind die Zahlen noch nicht da, wird die Meldung vorgemerkt und beim naechsten Takt
     * erneut geprueft: Ein Pet erreicht seine Hoechststufe einmal, das darf nicht an
     * einer Datei haengenbleiben, die gerade geholt wird.
     */
    private static void notePetLevel(String petId, String rarity, int level) {
        PetLevels.Table table = PetLevels.tableFor(petId, rarity);
        if (table == null) {
            pendingPets.add(new PendingPet(petId, rarity, level, System.currentTimeMillis()));
            ShokiMod.LOGGER.info("[Profit] {} reached {} - waiting for the pet levels", petId, level);
            return;
        }
        if (level != table.maxLevel()) return;
        petMaxed(petId, rarity, level);
    }

    /**
     * Ein Pet ist auf seiner Hoechststufe angekommen.
     *
     * Zwei Dinge, die nicht zusammengehoeren: Die Einblendung gilt immer, der Eintrag
     * im Kasten nur, wenn der Kasten laeuft.
     */
    private static void petMaxed(String petId, String rarity, int level) {
        PetMaxAlert.show(petId, rarity, level);
        if (!enabled()) return;

        String itemId = petId + ";" + tierOf(rarity) + "+" + level;
        adjust(itemId, 1);
        say(Component.literal(nameOf(itemId)).withStyle(ChatFormatting.WHITE)
                .append(Component.literal(" counts in the tracker.").withStyle(ChatFormatting.YELLOW)));
    }

    /** Eine Meldung, die auf die Stufen-Zahlen wartet */
    private record PendingPet(String petId, String rarity, int level, long at) {
    }

    private static final List<PendingPet> pendingPets = new ArrayList<>();
    /** So lange wird auf die Zahlen gewartet, danach ist die Meldung verfallen */
    private static final long PENDING_MILLIS = 60_000L;

    /** Vorgemerkte Meldungen erneut pruefen, sobald die Zahlen da sind */
    private static void retryPendingPets(long now) {
        if (pendingPets.isEmpty()) return;

        for (PendingPet warte : new ArrayList<>(pendingPets)) {
            PetLevels.Table table = PetLevels.tableFor(warte.petId(), warte.rarity());
            if (table == null) {
                if (now - warte.at() > PENDING_MILLIS) {
                    pendingPets.remove(warte);
                    ShokiMod.LOGGER.info("[Profit] gave up on {} - no pet levels", warte.petId());
                }
                continue;
            }
            pendingPets.remove(warte);
            if (warte.level() != table.maxLevel()) continue;
            petMaxed(warte.petId(), warte.rarity(), warte.level());
        }
    }

    /** Die Ziffer einer Seltenheit, wie sie in der Pet-Kennung steht */
    private static int tierOf(String rarity) {
        return switch (rarity == null ? "" : rarity.toUpperCase(Locale.ROOT)) {
            case "UNCOMMON" -> 1;
            case "RARE" -> 2;
            case "EPIC" -> 3;
            case "LEGENDARY" -> 4;
            case "MYTHIC" -> 5;
            default -> 0;
        };
    }

    /** Die Seltenheit hinter der Ziffer einer Pet-Kennung */
    private static String rarityOf(String tier) {
        return switch (tier) {
            case "1" -> "UNCOMMON";
            case "2" -> "RARE";
            case "3" -> "EPIC";
            case "4" -> "LEGENDARY";
            case "5" -> "MYTHIC";
            default -> "COMMON";
        };
    }

    /**
     * Die Seltenheit aus der Farbe vor dem Pet-Namen.
     *
     * Hypixel faerbt den Namen nach Seltenheit - gruen, blau, lila, gold, hellviolett.
     * Steht dort nichts (weil die Zeile schon entfaerbt ankam), gilt legendaer: Das ist
     * die haeufigste Stufe eines Pets, das jemand bis hundert spielt.
     */
    private static int tierFromColour(String formatted, String name) {
        if (formatted != null) {
            int at = formatted.indexOf(name);
            if (at > 1) {
                char code = formatted.charAt(at - 1);
                int tier = switch (code) {
                    case 'f' -> 0;
                    case 'a' -> 1;
                    case '9' -> 2;
                    case '5' -> 3;
                    case '6' -> 4;
                    case 'd' -> 5;
                    default -> -1;
                };
                if (tier >= 0) return tier;
            }
        }
        return 4;
    }

    /**
     * Die Farbe, in der der Name steht - die Seltenheit des Items.
     *
     * Drei Quellen, in dieser Reihenfolge: was beim Fund am Gegenstand selbst zu
     * sehen war, was diese Sitzung gesehen hat, und sonst die Seltenheit aus
     * Hypixels Item-Liste. Wer in keiner steht, bleibt weiss.
     */
    public static int colourOf(String itemId) {
        // Ein fertiges Pet traegt seine Seltenheit in der Kennung - die Item-Liste kennt
        // die Kennung nicht, die Farbe der Zeile soll sie trotzdem zeigen
        Matcher maxed = MAXED_PET.matcher(itemId == null ? "" : itemId);
        if (maxed.matches()) {
            int farbe = SkyBlockItems.rarityColour(rarityOf(maxed.group("tier")));
            if (farbe != 0) return farbe;
        }

        // Farben sind Divine, und das steht in keiner Liste: Hypixels Item-Liste kennt
        // keinen einzigen Divine-Eintrag, und im Inventar sieht die Mod eine Farbe nie -
        // sie kommt ueber die Chatzeile. Ohne diese Zeile stuende ein 200-Millionen-Fund
        // in Weiss zwischen den Knochen
        if (itemId != null && itemId.startsWith("DYE_")) return SkyBlockItems.rarityColour("DIVINE");

        Integer stored = cfg().colours.get(itemId);
        if (stored != null && stored != 0) return stored;

        int seen = ItemChanges.colourOf(itemId);
        if (seen != 0) return seen;

        int rarity = SkyBlockItems.rarityColour(ItemNames.tier(itemId));
        return rarity != 0 ? rarity : 0xFFFFFFFF;
    }

    /** Der Klang zu einer Ware, oder "" */
    public static String soundOf(String itemId) {
        String name = cfg().sounds.get(itemId);
        return name == null ? "" : name;
    }

    /** Einen Klang festlegen. Leer nimmt ihn wieder weg */
    public static void setSound(String itemId, String fileName) {
        if (itemId == null) return;
        if (fileName == null || fileName.isBlank()) cfg().sounds.remove(itemId);
        else cfg().sounds.put(itemId, fileName);
        ModConfig.INSTANCE.saveNow();
    }

    /**
     * Den Klang einer Ware spielen, hoechstens einmal je Sekunde.
     *
     * Wer in einem Sack-Schwung dreihundert Stueck bekommt, hoert sonst dreihundertmal
     * denselben Ton uebereinander. Die Sperre gilt je Ware: Zwei verschiedene Funde im
     * selben Augenblick duerfen beide klingen.
     */
    private static void playSound(String itemId) {
        String datei = soundOf(itemId);
        if (datei.isEmpty()) return;

        long now = System.currentTimeMillis();
        Long zuletzt = lastSound.get(itemId);
        if (zuletzt != null && now - zuletzt < SOUND_GAP_MILLIS) return;

        lastSound.put(itemId, now);
        com.shokiteufel.shokimod.util.CustomSoundPlayer.play(datei,
                com.shokiteufel.shokimod.util.AlertVolume.factor(), itemId);
    }

    /** Wann eine Ware zuletzt geklungen hat */
    private static final Map<String, Long> lastSound = new java.util.HashMap<>();
    private static final long SOUND_GAP_MILLIS = 1_000L;

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

        // Ein fertiges Pet steht in keiner Preisliste unter dieser Kennung - sein Preis
        // kommt aus derselben Auswertung, die auch /shoki petprofit fuellt
        Matcher maxed = MAXED_PET.matcher(itemId == null ? "" : itemId);
        if (maxed.matches()) {
            String rarity = rarityOf(maxed.group("tier"));
            String pet = maxed.group("pet");
            String art = pet.substring(0, pet.indexOf(';'));
            long preis = com.shokiteufel.shokimod.util.PetProfitData.maxedPrice(
                    art, rarity, Integer.parseInt(maxed.group("level")));
            if (preis <= 0) return -1;

            // Abgezogen wird, was ein frisch geschluepftes Tier derselben Art kostet.
            //
            // Das Einser war schon da, bevor es hochgezogen wurde - oft steht es sogar
            // selbst im Kasten, weil es als Fund hereinkam. Stuende hier der volle Preis
            // des fertigen Tiers, waere derselbe Wert zweimal gezaehlt. Angewachsen ist
            // allein der Unterschied, und das ist die Zahl, auf die es ankommt.
            //
            // Kennt die Liste keinen Einstiegspreis, bleibt es beim vollen - lieber eine
            // zu hohe Zahl als gar keine
            long einser = com.shokiteufel.shokimod.util.PetProfitData.freshPrice(art, rarity);
            return einser > 0 && einser < preis ? preis - einser : preis;
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

    /**
     * Die abgeschlossenen Tage, neuester zuerst.
     *
     * Der laufende Tag steht nicht darin - der steht im Kasten. Wer ihn hier auch sehen
     * will, bekommt ihn vom Fenster vorangestellt.
     */
    public static List<ModConfig.DayRecord> history() {
        return cfg().history;
    }

    /** Was ein abgeschlossener Tag heute wert waere */
    public static double worthOf(ModConfig.DayRecord tag) {
        if (tag == null) return 0;

        double sum = 0;
        for (Map.Entry<String, Integer> entry : tag.counts.entrySet()) {
            double unit = unitPrice(entry.getKey());
            if (unit > 0) sum += unit * entry.getValue();
        }
        return sum;
    }

    /** Der Klick auf die Ueberschrift: Lauf, Tag, alles, wieder Lauf */
    public static void cycleView() {
        ModConfig.ProfitView[] alle = ModConfig.ProfitView.values();
        cfg().view = alle[(view().ordinal() + 1) % alle.length];
        ModConfig.INSTANCE.saveNow();
    }

    /** Alle sichtbaren Zeilen, wertvollste zuerst */
    public static List<Row> rows() {
        Map<String, Long> mengen = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : activeCounts().entrySet()) {
            String itemId = entry.getKey();
            int count = entry.getValue() == null ? 0 : entry.getValue();
            if (itemId != null && (count > 0 || (count < 0 && isBait(itemId)))) {
                mengen.merge(itemId, (long) count, Long::sum);
            }
        }
        craftUp(mengen);

        List<Row> out = new ArrayList<>();
        for (Map.Entry<String, Long> entry : mengen.entrySet()) {
            String itemId = entry.getKey();
            long count = entry.getValue();
            if (count == 0 || (count < 0 && !isBait(itemId)) || !shown(itemId)) continue;

            double unit = unitPrice(itemId);
            out.add(new Row(itemId, nameOf(itemId), count, unit > 0 ? unit * count : 0,
                    unit > 0, modeOf(itemId)));
        }
        out.sort(Comparator.comparingDouble(Row::value).reversed().thenComparing(Row::name));
        return out;
    }

    /**
     * Zusammenlegen, was hochgerechnet werden soll - in ganzen Stuecken.
     *
     * Gecraftet wird nicht in Bruchteilen: Aus 400 Bones werden zwei Enchanted Bones,
     * und die achtzig, die uebrig bleiben, stehen weiter als Bones da. Erst wenn wieder
     * 160 zusammen sind, wandern sie eine Stufe hoeher.
     *
     * Mehrere Durchgaenge, weil Stufen aufeinander folgen koennen: Magmafish werden zu
     * Silbernen, und die Silbernen - eigene und gerade entstandene - zu Goldenen. Fuenf
     * Durchgaenge sind mehr als jede Kette im Spiel lang ist und enden trotzdem.
     */
    private static void craftUp(Map<String, Long> mengen) {
        for (int runde = 0; runde < 5; runde++) {
            boolean etwasGetan = false;
            for (String itemId : new ArrayList<>(mengen.keySet())) {
                String ziel = countAsOf(itemId);
                if (ziel == null) continue;

                // Stufe fuer Stufe bis zum eingestellten Ziel, nicht in einem Sprung:
                // Wer bis zum Block rechnet, sieht die uebrigen Enchanted Bones auch als
                // Enchanted Bones - und nicht wieder als 320 lose Bones
                String unten = itemId;
                for (CollectionData.Step stufe : CollectionData.chain(itemId, ziel)) {
                    long vorrat = mengen.getOrDefault(unten, 0L);
                    long ganze = vorrat / Math.max(1, stufe.perStep());
                    if (ganze > 0) {
                        mengen.put(unten, vorrat - ganze * stufe.perStep());
                        mengen.merge(stufe.itemId(), ganze, Long::sum);
                        etwasGetan = true;
                    }
                    unten = stufe.itemId();
                }
            }
            if (!etwasGetan) return;
        }
    }

    /**
     * Als welche Ware eine gefundene gezaehlt wird, oder null.
     *
     * Solange die Umrechnung noch nicht da ist - der Bauplan wird im Hintergrund geholt -
     * bleibt die Ware, was sie ist. Lieber eine Zeile zu viel als eine falsche Zahl.
     */
    public static String countAsOf(String itemId) {
        String ziel = cfg().countAs.get(itemId);
        return ziel == null || ziel.isBlank() || ziel.equals(itemId) ? null : ziel;
    }

    /**
     * Festlegen, als was eine Ware zaehlt. Null oder dieselbe Ware hebt es wieder auf.
     *
     * @return die Umrechnung, oder -1 wenn die beiden nichts miteinander zu tun haben
     */
    public static long setCountAs(String itemId, String targetId) {
        if (itemId == null) return -1;
        if (targetId == null || targetId.isBlank() || targetId.equals(itemId)) {
            cfg().countAs.remove(itemId);
            ModConfig.INSTANCE.saveNow();
            return 1;
        }

        long teiler = CollectionData.ratio(itemId, targetId);
        if (teiler <= 0) return -1;

        cfg().countAs.put(itemId, targetId);
        ModConfig.INSTANCE.saveNow();
        ShokiMod.LOGGER.info("[Profit] {} counts as {} ({} to one)", itemId, targetId, teiler);
        return teiler;
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

        // Ein neues SkyBlock-Jahr bricht den Tag ebenfalls um.
        //
        // Sonst gehoert der ganze Tag dem Jahr, in dem er angefangen hat - und der
        // Jahreswechsel liegt fast nie zur Reset-Stunde, sondern irgendwann mitten
        // darin. Am 03.10. sass deshalb bei einem Spieler alles vom ganzen Tag noch
        // im Jahr 517, obwohl 518 lief, und im Jahres-Reiter tauchte das neue Jahr
        // ueberhaupt nicht auf. Das ist kein Schoenheitsfehler: Ein Jahr dauert 124
        // Stunden, also fuenf Tage - ein falsch zugeordneter Tag ist ein Fuenftel.
        int jahrJetzt = com.shokiteufel.shokimod.util.SkyBlockYear.yearOf(now);
        boolean neuesJahr = cfg().dayStartedAt > 0L && jahrJetzt > 0
                && jahrJetzt != com.shokiteufel.shokimod.util.SkyBlockYear.yearOf(cfg().dayStartedAt);
        if (neuesJahr) {
            // Faellt beides zusammen, zaehlt der spaetere der beiden Zeitpunkte
            start = Math.max(start, com.shokiteufel.shokimod.util.SkyBlockYear.startOf(jahrJetzt));
        }
        if (cfg().dayStartedAt >= start) return;

        boolean stand = !cfg().dayCounts.isEmpty() || cfg().dayUptimeMillis > 0L;
        // Der abgelaufene Tag wandert ins Archiv, statt ersatzlos zu verschwinden
        if (stand && cfg().dayStartedAt > 0L) {
            cfg().history.add(0, new ModConfig.DayRecord(
                    cfg().dayStartedAt, cfg().dayUptimeMillis, cfg().dayCounts,
                    com.shokiteufel.shokimod.util.SkyBlockYear.mayor()));
            while (cfg().history.size() > ModConfig.ProfitCategory.MAX_HISTORY) {
                cfg().history.remove(cfg().history.size() - 1);
            }
        }
        cfg().dayCounts.clear();
        cfg().dayUptimeMillis = 0L;
        cfg().dayStartedAt = start;
        dirty = true;
        if (stand) {
            ShokiMod.LOGGER.info(neuesJahr
                    ? "[Profit] SkyBlock year {} started, the day count is back to zero"
                    : "[Profit] a new day started, the day count is back to zero", jahrJetzt);
        }
        if (neuesJahr) sayYearSummary(jahrJetzt - 1);
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

        // "Enchanted_Bone" ist gemeint wie "Enchanted Bone": Wer die Kennung im Kopf hat,
        // tippt Unterstriche, und daran soll der Befehl nicht scheitern
        if (gesucht.indexOf('_') >= 0) {
            ids = ItemNames.idsFor(gesucht.replace('_', ' '));
            if (!ids.isEmpty()) return ids.get(0);
        }

        // Und umgekehrt: Die Kennung selbst, ob gross geschrieben oder nicht. Gueltig ist
        // sie, wenn die Item-Liste einen Namen dazu kennt - dann gibt es die Ware auch,
        // ganz gleich ob im Kasten schon etwas von ihr steht
        String kennung = gesucht.replace(' ', '_').toUpperCase(Locale.ROOT);
        if (ItemNames.displayName(kennung) != null) return kennung;

        for (Map<String, Integer> zaehler : List.of(cfg().counts, cfg().dayCounts, cfg().totalCounts)) {
            for (String id : zaehler.keySet()) {
                if (nameOf(id).equalsIgnoreCase(gesucht) || id.equalsIgnoreCase(gesucht)) return id;
            }
        }

        // Und zuletzt die Zeilen, wie sie im Kasten stehen. Eine hochgerechnete Zeile
        // steht unter keiner dieser Kennungen: Wer alles in Fine zaehlen laesst, hat in
        // den Zaehlern Rough und Flawed, sieht aber "Fine Peridot x25" - und tippt genau
        // das. Vorher hiess es dann "No item called fine Peridot", obwohl es dastand
        for (Row row : rows()) {
            if (row.name().equalsIgnoreCase(gesucht)) return row.itemId();
        }
        return null;
    }

    /**
     * Was in die angezeigte Zeile dieser Ware hineingerechnet wird - ohne sie selbst.
     *
     * Wer alles in Fine zaehlen laesst, hat in den Zaehlern Rough und Flawed stehen;
     * die Zeile "Fine Peridot x25" entsteht erst beim Anzeigen. Eine Aenderung an
     * dieser Zeile muss deshalb auch die Waren treffen, aus denen sie kommt - sonst
     * setzt man sie auf null und sie steht beim naechsten Bild unveraendert da.
     */
    static List<String> feeding(String itemId) {
        if (itemId == null) return List.of();
        List<String> out = new ArrayList<>();
        for (Map<String, Integer> zaehler : List.of(cfg().counts, cfg().dayCounts, cfg().totalCounts)) {
            for (String quelle : zaehler.keySet()) {
                if (quelle.equals(itemId) || out.contains(quelle)) continue;
                if (itemId.equals(countAsOf(quelle))) out.add(quelle);
            }
        }
        return out;
    }

    /** Die Menge, wie sie im Kasten steht - mit allem, was hochgerechnet dazukommt */
    static int shownCount(String itemId) {
        for (Row row : rows()) {
            if (row.itemId().equals(itemId)) return (int) Math.min(Integer.MAX_VALUE, row.count());
        }
        return countOf(itemId);
    }

    /**
     * Die naechsten zwei Stufen ueber einer Ware - was der Knopf im Fenster anbietet.
     *
     * Erste Stelle ist "x1" (Bone zu Enchanted Bone), zweite "x2" (weiter zum Block).
     * Gesucht wird nicht im ganzen Katalog, sondern unter den wenigen Waren, die nach
     * dieser benannt sind und auf dem Basar gehandelt werden - so bleiben es drei, vier
     * Bauplaene statt vierzig, und Bone Necklace steht nicht darunter.
     *
     * Solange die Bauplaene geholt werden, ist die Liste kuerzer oder leer. Sie wird
     * deshalb erst gemerkt, wenn etwas darin steht.
     */
    /**
     * Stufen, deren Name sich nicht aus dem der Ware ergibt.
     *
     * Nur Farming - dort heisst die dritte Stufe oft anders als die zweite, und kein
     * Zusammensetzen des Namens kommt dahin. Eingetragen ist, wohin zu suchen ist; ob es
     * das Rezept gibt und mit welcher Zahl, steht im Bauplan, nicht hier.
     *
     * Weizen fehlt mit Absicht: Hay Bale und Enchanted Hay Bale haengen zusammen, aber das
     * NEU-Verzeichnis fuehrt fuer Enchanted Hay Bale kein Rezept. Ohne Rezept laesst sich
     * die Kette nicht rechnen, und Weizen bleibt bei Enchanted Wheat.
     */
    private static final Map<String, List<String>> IRREGULAR_NEXT = Map.ofEntries(
            Map.entry("PUMPKIN", List.of("POLISHED_PUMPKIN")),
            Map.entry("POTATO_ITEM", List.of("ENCHANTED_BAKED_POTATO")),
            Map.entry("NETHER_STALK", List.of("MUTANT_NETHER_STALK")),
            Map.entry("RED_MUSHROOM", List.of("ENCHANTED_RED_MUSHROOM", "ENCHANTED_HUGE_MUSHROOM_2")),
            Map.entry("BROWN_MUSHROOM", List.of("ENCHANTED_BROWN_MUSHROOM", "ENCHANTED_HUGE_MUSHROOM_1")));

    /**
     * Was nach einer Ware benannt ist, aber nie ihre naechste Stufe.
     *
     * Cropie-Stiefel, Feder-Ring, Fermento-Artefakt: Sie tragen den Namen der Ware, weil
     * sie aus ihr gebaut werden - aber aus vier, zwanzig oder hundertacht Stueck, und das
     * liest sich fuer den Vergleich wie eine kleinere Stufe als Enchanted. Der Knopf bot
     * dann Stiefel an. Gesperrt wird nur auf dem namensverwandten Weg; was Hypixel wirklich
     * als Stufe fuehrt (Enchanted, Compacted, Block), geht den anderen.
     */
    private static final java.util.regex.Pattern EQUIPMENT = java.util.regex.Pattern.compile(
            "_(BOOTS|LEGGINGS|CHESTPLATE|HELMET|TALISMAN|RING|ARTIFACT|RELIC|CHARM|SWORD|PICKAXE|AXE|"
                    + "HOE|SHOVEL|ROD|NECKLACE|CLOAK|BELT|GLOVES|GAUNTLET|BRACELET|HAT|CAP|TUNIC|TROUSERS)$");

    /** Aus einer Kandidatenliste die erste und zweite Stufe waehlen */
    private static List<String> tiersFrom(String itemId, Iterable<String> kandidaten) {
        String x1 = null;
        String x2 = null;
        long kleinsteStufe = Long.MAX_VALUE;
        long kleinsterWeg = Long.MAX_VALUE;
        List<CollectionData.Step> wegNachOben = List.of();
        for (String kandidat : kandidaten) {
            if (kandidat.equals(itemId) || MINION.matcher(kandidat).matches()) continue;

            List<CollectionData.Step> weg = CollectionData.chain(itemId, kandidat);
            if (weg.size() == 1 && weg.get(0).perStep() < kleinsteStufe) {
                kleinsteStufe = weg.get(0).perStep();
                x1 = kandidat;
            } else if (weg.size() == 2) {
                long gesamt = Math.max(1, weg.get(0).perStep()) * Math.max(1, weg.get(1).perStep());
                if (gesamt < kleinsterWeg) {
                    kleinsterWeg = gesamt;
                    x2 = kandidat;
                    wegNachOben = weg;
                }
            }
        }

        // Steht die zweite Stufe fest, sagt ihr eigener Weg, was die erste ist. Das ist
        // verlaesslicher als "die Ware mit der kleinsten Zahl": In einen Magmafish Hat
        // gehen weniger Magmafish als in einen silbernen, ein Hut ist aber keine Stufe
        if (x2 != null && !wegNachOben.isEmpty()) x1 = wegNachOben.get(0).itemId();

        List<String> stufen = new ArrayList<>();
        if (x1 != null) stufen.add(x1);
        if (x2 != null) stufen.add(x2);
        return stufen;
    }

    private static final java.util.Set<String> tierLogged = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public static List<String> upgradeTiers(String itemId) {
        if (itemId == null) return List.of();
        List<String> gemerkt = tierCache.get(itemId);
        if (gemerkt != null) return gemerkt;

        java.util.LinkedHashSet<String> kandidaten = new java.util.LinkedHashSet<>();
        kandidaten.add("ENCHANTED_" + itemId);
        kandidaten.add("ENCHANTED_" + itemId + "_BLOCK");
        kandidaten.add(itemId + "_BLOCK");
        // Edelsteine heissen nicht nacheinander, sondern jede Stufe anders
        int gem = itemId.indexOf("_GEM");
        int strich = itemId.indexOf('_');
        if (gem > 0 && strich > 0 && strich < gem) {
            String sorte = itemId.substring(strich + 1, gem);
            for (String stufe : List.of("FLAWED", "FINE")) kandidaten.add(stufe + "_" + sorte + "_GEM");
        }
        // Ueber den Anzeigenamen, nicht ueber die Kennung: Die Kennungen sind
        // historisch gewachsen und halten sich bei 36 der 165 verzauberten Waren
        // nicht an das Schema. End Stone heisst ENDER_STONE, die Stufe darueber aber
        // ENCHANTED_ENDSTONE - kein Zusammensetzen der Kennung kommt da hin, und
        // deshalb stand im Fenster ein Strich statt eines Knopfes. Betroffen ist
        // gerade das, was man oft farmt: Karotten, Kakao, Lapis, Eisen, Gold, jede
        // Holzart. Die Namen dagegen sind einheitlich - "Enchanted " davor genuegt,
        // und alle 36 Faelle loesen sich darueber auf
        String anzeige = ItemNames.displayName(itemId);
        if (anzeige != null && !anzeige.isBlank()) {
            kandidaten.addAll(ItemNames.idsFor("Enchanted " + anzeige));
            kandidaten.addAll(ItemNames.idsFor("Enchanted " + anzeige + " Block"));
            kandidaten.addAll(ItemNames.idsFor(anzeige + " Block"));
            // Die dritte Stufe der Garden-Blumen heisst Compacted: Sunflower, Moonflower
            // und Wild Rose haben Enchanted und dann Compacted, nichts dazwischen
            kandidaten.addAll(ItemNames.idsFor("Compacted " + anzeige));
        }
        kandidaten.add("COMPACTED_" + itemId);
        // Fermento wird zu Condensed Fermento: dieselbe Idee mit anderem Vorsatz
        kandidaten.add("CONDENSED_" + itemId);
        // Was sich nicht aus dem Namen ableiten laesst: Hypixel nennt manche Stufen anders
        // als die davor - Polished Pumpkin, Enchanted Baked Potato, Mutant Nether Wart,
        // dazu die Pilz-Bloecke unter ihrer Huge-Mushroom-Kennung. Die Liste sagt nur, wohin
        // zu suchen ist; ob es das Rezept wirklich gibt, entscheidet der Bauplan
        kandidaten.addAll(IRREGULAR_NEXT.getOrDefault(itemId, List.of()));

        // Und was sonst nach der Ware benannt ist - Silver und Gold Magmafish zum
        // Beispiel. Hoechstens acht, damit aus einem Namen wie BONE keine Handvoll
        // Bauplan-Abfragen fuer Halsketten und Bumerangs wird.
        //
        // Getrennt gehalten, weil es der unsicherere Weg ist: "LEATHER_" findet auch
        // LEATHER_BOOTS, und vier Leder in einem Stiefel sahen aus wie eine kleinere Stufe
        // als die 160 fuer Enchanted Leather - also bot der Knopf Stiefel an
        java.util.LinkedHashSet<String> sekundaer = new java.util.LinkedHashSet<>();
        int weitere = 0;
        for (String id : ItemNames.allIds()) {
            if (weitere >= 8) break;
            if (id.startsWith(itemId + "_") && !kandidaten.contains(id)
                    && !EQUIPMENT.matcher(id).find() && sekundaer.add(id)) weitere++;
        }

        // Erst die sicheren Wege. Nur wenn die gar nichts hergeben, kommen die
        // namensverwandten dazu - sonst gewinnt eine Stiefel-Stufe gegen Enchanted Leather
        List<String> fertig = List.copyOf(tiersFrom(itemId, kandidaten));
        if (fertig.isEmpty() && !sekundaer.isEmpty()) {
            java.util.LinkedHashSet<String> alle = new java.util.LinkedHashSet<>(kandidaten);
            alle.addAll(sekundaer);
            fertig = List.copyOf(tiersFrom(itemId, alle));
        }
        // Gemerkt wird erst, wenn beide Stufen dastehen: Wer zu frueh merkt, merkt sich
        // die halbe Antwort - und die blieb dann stehen, obwohl die zweite Stufe kurz
        // darauf ankam
        if (fertig.size() == 2) tierCache.put(itemId, fertig);
        // Einmal je Ware ins Log, was gefunden wurde: fehlt eine Stufe, steht hier warum
        if (tierLogged.add(itemId + "|" + fertig.size())) {
            ShokiMod.LOGGER.info("[Profit] crafted-up forms of {}: {} (from {} candidates)",
                    itemId, fertig, kandidaten.size());
        }
        return fertig;
    }

    /**
     * Ein Minion ist keine Stufe.
     *
     * In einen Hard Stone Minion III gehen Enchanted Hard Stones, also sieht er fuer den
     * Bauplan aus wie die naechste Stufe ueber Hard Stone. Er ist aber ein Geraet und
     * keine Ware, die man verkauft - und stand damit als "x2" im Knopf.
     */
    private static final java.util.regex.Pattern MINION =
            java.util.regex.Pattern.compile(".*_GENERATOR_\\d+$");

    /** Einmal gefundene Stufen bleiben - die Bauplaene aendern sich nicht im Spiel */
    private static final Map<String, List<String>> tierCache = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Vorschlaege, als was sich eine Ware zaehlen laesst.
     *
     * Vorgeschlagen wird, was nach ihr benannt ist - Enchanted Bone und Enchanted Bone
     * Block heissen nach dem Bone - und bei Edelsteinen die hoeheren Stufen derselben
     * Sorte, die anders heissen. Ob es wirklich passt, entscheidet spaeter der Bauplan.
     */
    public static List<String> countAsCandidates(String itemName) {
        String itemId = idForName(itemName);
        if (itemId == null) return List.of();

        java.util.LinkedHashSet<String> ids = new java.util.LinkedHashSet<>();
        for (String id : ItemNames.allIds()) {
            // Ganzes Wort, nicht irgendwo enthalten: Sonst stuende der Bonemerang unter
            // den Vorschlaegen fuer Bone, und Flexbone gleich daneben
            if (id.equals(itemId)) continue;
            if (id.startsWith(itemId + "_") || id.endsWith("_" + itemId)
                    || id.contains("_" + itemId + "_")) {
                ids.add(id);
            }
        }
        // Edelsteine: Rough, Flawed, Fine und Flawless heissen nicht nacheinander
        int gem = itemId.indexOf("_GEM");
        int strich = itemId.indexOf('_');
        if (gem > 0 && strich > 0 && strich < gem) {
            String sorte = itemId.substring(strich + 1, gem);
            for (String stufe : List.of("FLAWED", "FINE", "FLAWLESS")) {
                String id = stufe + "_" + sorte + "_GEM";
                if (!id.equals(itemId) && ItemNames.displayName(id) != null) ids.add(id);
            }
        }

        // Nach Namen, und jeden nur einmal: Zwei Kennungen koennen denselben tragen
        java.util.TreeSet<String> namen = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (String id : ids) namen.add(nameOf(id));
        return new ArrayList<>(namen);
    }

    /**
     * Der Befehl /shoki profittracker <Ware> countas <Ziel>.
     *
     * "none" hebt es auf. Passt das Ziel nicht zur Ware - kein Bauplan fuehrt von der
     * einen zur anderen -, bleibt alles, wie es war, und der Chat sagt warum.
     */
    public static void applyCountAs(String itemName, String targetName) {
        String itemId = idForName(itemName);
        if (itemId == null) {
            say(Component.literal("No item called ").withStyle(ChatFormatting.RED)
                    .append(Component.literal(itemName).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(".").withStyle(ChatFormatting.RED)));
            return;
        }

        String ziel = targetName == null ? "" : targetName.trim();
        if (ziel.isEmpty() || ziel.equalsIgnoreCase("none") || ziel.equalsIgnoreCase("off")) {
            setCountAs(itemId, null);
            say(Component.literal(nameOf(itemId)).withStyle(ChatFormatting.WHITE)
                    .append(Component.literal(" counts as itself again.").withStyle(ChatFormatting.YELLOW)));
            return;
        }

        String targetId = idForName(ziel);
        if (targetId == null) {
            say(Component.literal("No item called ").withStyle(ChatFormatting.RED)
                    .append(Component.literal(ziel).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(".").withStyle(ChatFormatting.RED)));
            return;
        }

        long teiler = setCountAs(itemId, targetId);
        if (teiler <= 0) {
            say(Component.literal("No recipe leads from ").withStyle(ChatFormatting.RED)
                    .append(Component.literal(nameOf(itemId)).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(" to ").withStyle(ChatFormatting.RED))
                    .append(Component.literal(nameOf(targetId)).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(" - or it is still being looked up. Try again in a moment.")
                            .withStyle(ChatFormatting.RED)));
            return;
        }

        say(Component.literal(nameOf(itemId)).withStyle(ChatFormatting.WHITE)
                .append(Component.literal(" now counts as ").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(nameOf(targetId)).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" - " + teiler + " to one.").withStyle(ChatFormatting.YELLOW)));
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

        // Die Zahl, die im Kasten steht - sonst meldet die Antwort "25 -> 0" als "0 -> 0",
        // wenn die Zeile hochgerechnet war
        int vorher = shownCount(itemId);
        if (change == Change.SET) {
            // Alle drei auf dieselbe Zahl. Wer eine Zahl geradezieht, meint die Ware -
            // und drei verschiedene Staende derselben Ware sind genau das, was er
            // loswerden wollte
            setAll(itemId, amount);
            say(Component.literal(nameOf(itemId)).withStyle(ChatFormatting.WHITE)
                    .append(Component.literal(": " + vorher + " -> " + amount
                            + " in Session, Day and Total.").withStyle(ChatFormatting.YELLOW)));
            return;
        }

        if (change == Change.REMOVE) {
            int weg = removeShown(itemId, amount);
            say(Component.literal(nameOf(itemId)).withStyle(ChatFormatting.WHITE)
                    .append(Component.literal(": " + vorher + " -> " + Math.max(0, vorher - weg)
                            + " in " + view() + ", and the same step in the other two.")
                            .withStyle(ChatFormatting.YELLOW)));
            return;
        }

        int delta = change == Change.ADD ? amount : -amount;
        if (delta == 0) {
            say(Component.literal(nameOf(itemId)).withStyle(ChatFormatting.WHITE)
                    .append(Component.literal(" already stands at " + vorher + ".")
                            .withStyle(ChatFormatting.YELLOW)));
            return;
        }

        adjust(itemId, delta);
        say(Component.literal(nameOf(itemId)).withStyle(ChatFormatting.WHITE)
                .append(Component.literal(": " + vorher + " -> " + Math.max(0, vorher + delta)
                        + " in " + view() + ", and the same step in the other two.")
                        .withStyle(ChatFormatting.YELLOW)));
    }

    /**
     * Dieselbe Zahl in allen drei Zeitraeumen. Null heisst: raus aus der Liste.
     *
     * Mitsamt dem, was hochgerechnet in diese Zeile fliesst: Stuenden die Rough weiter
     * in den Zaehlern, waere die Zeile beim naechsten Bild wieder da - und wer eine
     * Zahl geradezieht, meint die Zeile, die er sieht.
     */
    private static void setAll(String itemId, int amount) {
        for (String quelle : feeding(itemId)) {
            for (Map<String, Integer> zaehler : List.of(cfg().counts, cfg().dayCounts, cfg().totalCounts)) {
                zaehler.remove(quelle);
            }
        }
        for (Map<String, Integer> zaehler : List.of(cfg().counts, cfg().dayCounts, cfg().totalCounts)) {
            if (amount > 0) zaehler.put(itemId, amount);
            else zaehler.remove(itemId);
        }
        ModConfig.INSTANCE.saveNow();
        ShokiMod.LOGGER.info("[Profit] {} set to {} in all three", itemId, amount);
    }

    /**
     * Abziehen, was im Kasten steht - notfalls aus den Waren, aus denen es gerechnet ist.
     *
     * Erst die Ware selbst, dann die Quellen. Eine Quelle wird in ihrer eigenen Einheit
     * abgezogen: Ein Fine Peridot entsteht aus achtzig Flawed, also kostet ein Stueck
     * weniger in der Zeile achtzig Stueck weniger im Zaehler. Gerechnet wird in ganzen
     * Stuecken - was nicht aufgeht, bleibt als Rest stehen, so wie es auch beim
     * Hochrechnen stehen bleibt.
     *
     * @return wie viel tatsaechlich wegging, in der Einheit der Zeile
     */
    private static int removeShown(String itemId, int amount) {
        int offen = amount;
        int direkt = Math.min(offen, countOf(itemId));
        if (direkt > 0) {
            adjust(itemId, -direkt);
            offen -= direkt;
        }
        for (String quelle : feeding(itemId)) {
            if (offen <= 0) break;
            long jeStueck = CollectionData.ratio(quelle, itemId);
            if (jeStueck <= 0) continue;
            long gebraucht = Math.min(countOf(quelle), (long) offen * jeStueck);
            long ganze = gebraucht / jeStueck;
            if (ganze <= 0) continue;
            adjust(quelle, (int) -(ganze * jeStueck));
            offen -= (int) ganze;
        }
        return amount - offen;
    }

    /**
     * Was das abgelaufene SkyBlock-Jahr eingebracht hat, als Zeile im Chat.
     *
     * Ein Jahr dauert 124 Stunden; was darin zusammenkam, verschwindet sonst still in
     * einem Reiter, in den niemand schaut. Die Zeile ist anklickbar und fuehrt nicht in
     * die Jahresliste, sondern gleich in die Waren dieses Jahres - die Frage dahinter
     * ist ja nicht "wie viel", sondern "womit".
     *
     * Bewertet wird zu den Preisen von jetzt, so wie ueberall im Kasten. Stand nichts
     * darin, bleibt es still: Eine Null zu melden ist keine Nachricht.
     */
    private static void sayYearSummary(int jahr) {
        if (jahr <= 0) return;
        ModConfig.DayRecord satz = com.shokiteufel.shokimod.gui.DayProfitScreen.yearRecord(jahr);
        if (satz == null || satz.counts.isEmpty()) return;

        double wert = worthOf(satz);
        if (wert <= 0) return;

        String befehl = "/shoki dayprofit " + jahr;
        say(Component.literal("You earned ").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(ItemValue.format(Math.round(wert)))
                        .withStyle(ChatFormatting.GREEN))
                .append(Component.literal(" coins in SkyBlock Year " + jahr + ". ")
                        .withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("[Click here]")
                        .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
                        .withStyle(style -> style
                                .withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand(befehl))
                                .withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(
                                        Component.literal(satz.counts.size()
                                                + " kinds of item - click for the list"))))));
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
        if (itemId == null || countOf(itemId) == 0) return;

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
