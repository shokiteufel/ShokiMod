package com.shokiteufel.shokimod.scanner;

import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.data.FeatureGate;
import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.data.RareLootParser;
import com.shokiteufel.shokimod.data.RareLootParser.Drop;
import com.shokiteufel.shokimod.util.ItemValue;
import com.shokiteufel.shokimod.util.ItemNames;
import com.shokiteufel.shokimod.util.SkyBlockItems;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
 *
 * Zwei Sorten kommen ohne Umweg ueber das Inventar herein und haben deshalb je eine
 * eigene Quelle: Was in einen Sack faellt, meldet Hypixel gesammelt als "[Sacks] +38
 * items." mit der Aufstellung am Mauszeiger. Shards wandern beim Fang direkt in die
 * Hunting Box - fuer sie ist die Chatzeile ("You caught x2 Bambo Shards!", "CHARM!
 * ...") die einzige Meldung, dieselbe, die schon der Hunting Tracker liest.
 */
public final class ItemChanges {

    /** Wie lange nichts passieren muss, bis der Stand nach einem Wechsel wieder gilt (Ticks) */
    private static final int SETTLE_TICKS = 20;
    /** So lange wird ein Abgang gegen einen spaeteren Zugang derselben Ware aufgerechnet */
    private static final long OFFSET_MILLIS = 10_000L;
    /**
     * So lange zaehlt ein Abgang noch gegen eine Sack-Meldung.
     *
     * Hypixel fasst die Saecke zusammen und meldet sie gesammelt - die Zeile selbst
     * sagt "(Last 20s.)". Bis die Meldung kommt, ist der Abgang im Inventar also
     * laengst geschehen, und mit den zehn Sekunden von oben waere er vergessen,
     * bevor er gebraucht wird.
     */
    private static final long SACK_OFFSET_MILLIS = 65_000L;
    /**
     * Der letzte Platz der Schnellleiste zaehlt nicht mit.
     *
     * Dort steht gewoehnlich das SkyBlock-Menue, aber der Platz ist in Wahrheit eine
     * Anzeige: Wer eine Angel in die Hand nimmt, sieht dort seinen Koeder, wer einen
     * Bogen zieht, seine Pfeile. Beides liegt in Wirklichkeit im Lager und ist nicht
     * dazugekommen - gezaehlt sah es aber aus wie ein Fund, und beim naechsten Griff
     * zur Angel wieder.
     *
     * Deshalb beides: der Platz bleibt aussen vor, und das Menue zusaetzlich an seiner
     * Kennung - wer es verschoben hat, soll es auch dort nicht mitzaehlen.
     */
    /** So weit darf das liegengelassene Stueck entfernt sein, in Bloecken */
    private static final double FLOOR_REACH = 16.0;
    private static final int DISPLAY_SLOT = 8;
    private static final String MENU_ID = "SKYBLOCK_MENU";

    private static final Pattern COLOUR_CODE = Pattern.compile("§.");
    private static final Pattern LEADING_SYMBOLS = Pattern.compile("^[^\\p{L}\\p{N}]+");
    private static final Pattern TRAILING_SYMBOLS = Pattern.compile("[^\\p{L}\\p{N}]+$");
    /** "+38 Enchanted Helix Log (Enchanted Foraging Sack)" */
    private static final Pattern SACK_LINE = Pattern.compile("^([+-])\\s*([\\d,.]+)\\s+(.+?)\\s*\\(");
    private static final String SACK_MARKER = "[Sacks]";
    /**
     * "You received 32x Enchanted Sunflower for killing a Lunar Moth!"
     *
     * Die Zeile sagt nicht, was alles kommt - sie sagt, dass der Server gerade Beute
     * austeilt. Die Ultimate-Sunset-Buecher der Garden-Schaedlinge zum Beispiel haben
     * gar keine eigene Zeile; sie liegen wortlos im Inventar.
     */
    private static final Pattern KILL_REWARD = Pattern.compile(
            "^You received [\\d,]+x? .+ for killing (?:an?|the) .+!$", Pattern.CASE_INSENSITIVE);
    /** So lange nach einer solchen Zeile zaehlt auch, was bei offenem Fenster ankommt */
    private static final long LOOT_MILLIS = 5_000L;

    /**
     * "You Supercrafted Blessed Bait x256!" - gebaut, nicht gefunden.
     *
     * Zwei Formen. Entweder steht die Menge als "x9,432" hinter dem Namen, oder die
     * Zeile kam mehrfach und der Chat hat sie zu "...! (14)" zusammengefasst - so wie
     * er es daneben auch mit "Putting goods in escrow... (7)" macht.
     */
    private static final Pattern SUPERCRAFT = Pattern.compile(
            "^You Supercrafted (?<item>.+?)(?: x(?<amount>[\\d,]+))?!(?: \\((?<repeat>[\\d,]+)\\))?$",
            Pattern.CASE_INSENSITIVE);
    /** So lange gilt eine zusammengefasste Zeile als dieselbe wie die davor */
    private static final long REPEAT_MILLIS = 60_000L;
    private static String lastCraft = "";
    private static int lastCraftRepeat = 0;
    private static long lastCraftMillis = 0L;
    /**
     * "Moved 9 Enchanted Bone from your Sacks to your inventory." - geholt, nicht gefunden.
     *
     * Wer Ware aus einem Sack ins Inventar holt - von Hand oder ueber den Knopf, den
     * SkyHanni unter die Craft-Meldung setzt -, hat nichts gefunden. Gezaehlt wurde sie
     * schon, als sie in den Sack fiel; im Inventar sieht der Vergleich sie ein zweites
     * Mal, und ohne diese Zeile stuende sie doppelt im Kasten.
     */
    private static final Pattern FROM_SACKS = Pattern.compile(
            "^Moved (?<amount>[\\d,]+) (?<item>.+?) from your Sacks? to your inventory\\.?$",
            Pattern.CASE_INSENSITIVE);
    /**
     * "You have successfully transferred your items from this stash to your sacks!"
     *
     * Was aus dem Stash kommt, ist alles Moegliche - aber nichts davon ist gerade
     * gefunden worden. Die Sack-Meldung danach zaehlt den ganzen Schwung, im Bild vom
     * 29.09. waren es 143.755 Stueck auf einmal.
     */
    private static final Pattern STASH_TO_SACKS = Pattern.compile(
            "^You have successfully transferred your items from .+ stash to your sacks!?$",
            Pattern.CASE_INSENSITIVE);
    /**
     * So lange nach der Stash-Zeile zaehlt keine Sack-Meldung.
     *
     * Die Meldung fasst bis zu zwanzig Sekunden zusammen und kommt entsprechend spaet;
     * dreissig Sekunden decken das ab. Der Preis ist benannt: Was in diesen Sekunden
     * wirklich gefunden und eingelagert wird, faellt mit weg.
     */
    private static final long STASH_MILLIS = 30_000L;
    /** "[Bazaar] Claimed 1,344x Enchanted Raw Cod worth 1.1M coins bought for 841 each!" */
    private static final Pattern BAZAAR_CLAIM = Pattern.compile(
            "^" + Pattern.quote("[Bazaar]") + " Claimed (?<amount>[\\d,]+)x (?<item>.+?) worth .+$",
            Pattern.CASE_INSENSITIVE);
    /**
     * "[Bazaar] Cancelled! Refunded 897x Party Gift from cancelling Sell Offer!"
     *
     * Eine zurueckgezogene Verkaufsorder gibt die Ware zurueck. Sie war nie weg - sie lag
     * nur eine Weile im Basar - und ist damit kein Fund, sondern eine Rueckgabe.
     */
    private static final Pattern BAZAAR_REFUND = Pattern.compile(
            "^" + Pattern.quote("[Bazaar]") + " Cancelled! Refunded (?<amount>[\\d,]+)x (?<item>.+?)"
                    + " from cancelling .+$",
            Pattern.CASE_INSENSITIVE);
    /**
     * "You equipped MF!" - ein Satz Ruestung auf einmal, vom Server gelegt.
     *
     * Beim Wechsel eines Loadouts raeumt Hypixel die Ruestung paketweise um: Erst liegt
     * das alte Teil im Inventar, kurz darauf verschwindet es aus dem Ruestungsplatz.
     * Zwischen beiden Paketen kann ein Tick liegen, und in genau diesem Tick sieht der
     * Vergleich einen Zugang, der keiner ist - im Log vom 01.10. um 03:03:10 war das ein
     * Sorrow-Satz, vier Teile, mit Alarm und allem.
     *
     * Dass Ruestung mitgezaehlt wird (seit 1.9.10), hilft hier nicht: Sie faengt den
     * Umzug nur auf, wenn beide Haelften im selben Tick ankommen.
     */
    private static final Pattern EQUIPPED = Pattern.compile("^You equipped .+!$");
    /**
     * So viele Ticks wartet ein Zugang, bevor er zaehlt.
     *
     * Ein Umzug kommt nicht immer in einem Stueck an: Beim Loadout-Wechsel liegt das
     * alte Ruestungsteil schon im Inventar, waehrend es im Ruestungsplatz noch steht -
     * und erst im naechsten Tick verschwindet es dort. Wer sofort zaehlt, zaehlt diesen
     * Zwischenstand. Drei Ticks sind eine Sechzehntelsekunde fuer den Spieler und reichen
     * fuer beide Haelften eines Umzugs.
     */
    private static final int HOLD_TICKS = 3;

    /**
     * Ist das ein Ruestungs- oder Ausruestungsteil? Je Kennung einmal festgestellt.
     *
     * Nur solche Teile sind es, die ein Loadout-Wechsel ins Inventar legt - und nur sie
     * duerfen in der Sekunde danach aus der Zaehlung fallen. Alles andere, ein Pet etwa,
     * das in genau dieser Sekunde faellt, ist ein echter Fund.
     */
    private static final Map<String, Boolean> gearById = new HashMap<>();
    private static final Pattern GEAR_LINE = Pattern.compile(
            "(HELMET|CHESTPLATE|LEGGINGS|BOOTS|NECKLACE|CLOAK|BELT|GLOVES|GAUNTLET|BRACELET)");
    /** Wann zuletzt ein Loadout angelegt wurde - und ob die Sekunde danach noch aussteht */
    private static long lastSwapAt = 0L;
    private static boolean swapPending = false;
    /** So lange nach einem Wechsel gilt ein Zugang durch das Fenster noch als Teil davon */
    private static final long SWAP_WINDOW_MILLIS = 8_000L;

    private static boolean looksLikeGear(ItemStack stack) {
        // Ein Pet ist ein Spielerkopf, und Koepfe lassen sich aufsetzen - daran darf es nicht
        // haengen: Der Baby Yeti galt deshalb als Ruestung und fiel nach einem Wechsel aus der Zaehlung
        net.minecraft.world.item.component.CustomData data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (data != null && "PET".equals(data.copyTag().getStringOr("id", ""))) return false;
        if (stack.is(net.minecraft.world.item.Items.PLAYER_HEAD)) return loreSaysGear(stack);
        if (stack.get(net.minecraft.core.component.DataComponents.EQUIPPABLE) != null) return true;
        return loreSaysGear(stack);
    }

    private static boolean loreSaysGear(ItemStack stack) {
        net.minecraft.world.item.component.ItemLore lore = stack.get(net.minecraft.core.component.DataComponents.LORE);
        if (lore == null || lore.lines().isEmpty()) return false;
        // Die letzte Zeile nennt Seltenheit und Art: "MYTHIC HELMET", "LEGENDARY NECKLACE"
        String last = lore.lines().get(lore.lines().size() - 1).getString().toUpperCase(Locale.ROOT);
        return GEAR_LINE.matcher(last).find();
    }

    private static boolean isGear(String itemId) {
        return Boolean.TRUE.equals(gearById.get(itemId)) || worn.containsKey(itemId);
    }

    /** Dieselben Zugaenge ohne Ruestung und Ausruestung */
    private static Map<String, Integer> withoutGear(Map<String, Integer> gains) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : gains.entrySet()) {
            if (!isGear(entry.getKey())) out.put(entry.getKey(), entry.getValue());
        }
        return out;
    }

    /** Ein Zugang, der noch wartet - und der Tick, in dem er gesehen wurde */
    private record Held(Map<String, Integer> gains, int tick) {
    }

    private static final List<Held> held = new ArrayList<>();
    private static int tickCount = 0;

    private static final String SHARD_PREFIX = "SHARD_";
    /** Zeilen aus fremden Kanaelen erzaehlen von fremden Funden */
    private static final String[] FOREIGN_PREFIXES = {"Party >", "Guild >", "Co-op >", "From ", "To "};

    private static final List<Consumer<Map<String, Integer>>> listeners = new ArrayList<>(2);

    /**
     * Die Farbe, in der das Spiel den Namen einer Ware schreibt.
     *
     * Wird beim Zaehlen nebenbei mitgenommen, solange sie noch fehlt. Sie ist die
     * Seltenheit: blau ist selten, lila episch. Aus einer Liste laesst sich das nicht
     * fuer alles holen - Shards, Pets und Farben stehen in keiner.
     */
    private static final Map<String, Integer> colours = new HashMap<>();

    /** Der Stand des letzten Vergleichs: Kennung -> Stueckzahl im Inventar */
    private static Map<String, Integer> previousCounts = null;
    /** Fingerabdruck der Plaetze. Aendert er sich nicht, muss auch nicht gezaehlt werden */
    private static int previousSignature = 0;
    private static String previousContext = null;
    /**
     * Ticks, die nach einem Wechsel noch abzuwarten sind.
     *
     * Ein Zaehler, kein Ruhe-Erfordernis: Er laeuft in jedem Tick herunter, egal was
     * im Inventar gerade passiert. Die erste Fassung wartete stattdessen darauf, dass
     * sich zwanzig Ticks lang nichts ruehrt - und genau das kann ausbleiben. Wer
     * ununterbrochen etwas aufsammelt, kam nie aus dem Warten heraus, und dann zaehlte
     * der Kasten still gar nichts mehr.
     */
    private static int settleTicks = SETTLE_TICKS;
    /**
     * Kam die Wartezeit von einem Fenster oder von einem Wechsel?
     *
     * Der Unterschied entscheidet, ob sie abgebrochen werden darf. Nach einem Wechsel
     * nicht - dort kommt das halbe Inventar nach und saehe wie ein Fund aus. Nach einem
     * Fenster schon: Wenn der Server in genau dieser Sekunde Beute austeilt, gehoert
     * sie gezaehlt, und die Wartezeit war fuer Craft-Ergebnisse gedacht, nicht fuer sie.
     */
    private static boolean settleAfterWindow = false;
    /** Der Stand, als ein Fenster aufging - um danach zu sehen, was hindurchkam */
    private static Map<String, Integer> windowBaseline = null;
    /**
     * Bis wann der Server gerade Beute austeilt.
     *
     * Wer seine Fallen leert, hat dabei das Fallen-Menue offen, und die Beute kommt
     * waehrenddessen herein. Ohne diese Ausnahme faellt sie unter dieselbe Regel wie
     * ein Basar-Kauf und zaehlt nie - im Log stand genau das: zweimal vier Buecher,
     * beide Male "came in through a window".
     */
    private static long lootUntil = 0L;
    /** Bis wann eine Sack-Meldung noch zum geleerten Stash gehoert */
    private static long stashUntil = 0L;
    /**
     * Abgaenge der letzten Sekunden, gegen die spaetere Zugaenge verrechnet werden.
     *
     * Jeder Posten mit eigener Uhrzeit, weil die beiden Faelle unterschiedlich lange
     * nachwirken: das Wiederaufheben von Weggeworfenem zehn Sekunden, die Sack-Meldung
     * gut eine Minute.
     */
    private static final List<Loss> losses = new ArrayList<>();

    /**
     * Ein vorgemerkter Posten: so viele Stueck dieser Ware sind schon verbucht.
     *
     * {@code sackOnly} trennt die beiden Faelle, und diese Trennung ist wichtiger als
     * sie aussieht. Ein Abgang aus dem Inventar darf gegen alles aufgerechnet werden -
     * wer etwas wegwirft und wieder aufhebt, hat nichts gefunden. Was dagegen durch ein
     * Fenster hereinkam, ist nur fuer die Sack-Meldung vorgemerkt: Gekauftes wandert von
     * selbst in die Saecke und kaeme sonst als Fund zurueck. Gegen einen spaeteren Fund
     * derselben Ware im Inventar darf es nichts ausrichten - sonst frisst ein Einkauf
     * eine Minute lang jeden echten Fund derselben Ware.
     */
    private static final class Loss {
        final String itemId;
        int amount;
        /** Nicht endgueltig: Solange das Stueck noch auf dem Boden liegt, wird er nachgezogen */
        long at;
        final boolean sackOnly;

        Loss(String itemId, int amount, long at, boolean sackOnly) {
            this.itemId = itemId;
            this.amount = amount;
            this.at = at;
            this.sackOnly = sackOnly;
        }
    }

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
        return settleTicks == 0 && previousCounts != null && windowBaseline == null;
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

        tickCount++;
        flushHeld();
        rescueConfirmed();

        // Ein offener Behaelter ist der Weg, auf dem Gekauftes, Ausgelagertes und
        // Gecraftetes hereinkommt. Nichts davon ist ein Fund
        boolean windowOpen = client.screen instanceof AbstractContainerScreen<?>;
        // Solange Beute fliesst, wird auch bei offenem Fenster gezaehlt - aber nur so
        // lange: was man danach im Fenster anklickt, ist wieder ein Umzug, kein Fund
        boolean lootFlowing = System.currentTimeMillis() < lootUntil;
        if (windowOpen && !lootFlowing) {
            // Ohne Vergleichsstand gibt es auch nichts zu vergleichen: dann ist das
            // Fenster waehrend eines Wechsels aufgegangen, und das halbe Inventar saehe
            // hinterher aus wie frisch hereingekommen
            if (windowBaseline == null && previousCounts != null) windowBaseline = previousCounts;
            return;
        }

        String context = context(client);
        if (!context.equals(previousContext)) {
            previousContext = context;
            // Nach einem Wechsel kommt das Inventar Stueck fuer Stueck an; erst danach
            // ist ein Vergleich etwas wert
            settleTicks = SETTLE_TICKS;
            settleAfterWindow = false;
            previousCounts = null;
            windowBaseline = null;
            return;
        }

        if (windowBaseline != null && !windowOpen) {
            // Das Fenster ist zu. Der Stand von jetzt ist der neue Vergleichspunkt -
            // was durch das Fenster kam, zaehlt nicht als Fund
            Map<String, Integer> current = counts(client);
            noteWindow(windowBaseline, current);
            windowBaseline = null;
            previousCounts = current;
            previousSignature = signature(client);
            // Was beim Bauen oder Kaufen entsteht, kommt manchmal erst ein paar Ticks
            // nach dem Schliessen an. Diese Sekunde gehoert noch zum Fenster - ausser
            // der Server teilt gerade Beute aus, dann faellt sie genau in die Sekunde,
            // in der die Buecher der Schaedlinge ankommen
            if (!lootFlowing) {
                settleTicks = SETTLE_TICKS;
                settleAfterWindow = true;
            }
            return;
        }

        if (settleTicks > 0) {
            settleTicks--;
            if (settleTicks == 0) {
                Map<String, Integer> current = counts(client);
                // Was in dieser Sekunde ankam, zaehlt nicht - aber es soll nicht
                // spurlos verschwinden. Genau hier waren die vier Buecher weg, und im
                // Log stand nichts darueber
                if (previousCounts != null) {
                    if (swapPending) {
                        Map<String, Integer> echt = withoutGear(onlyGains(previousCounts, current));
                        if (!echt.isEmpty()) {
                            ShokiMod.LOGGER.info("[Profit] arrived during the armour swap, counted: {}", echt);
                            held.add(new Held(echt, tickCount));
                        }
                    }
                    if (debug()) {
                        Map<String, Integer> verschluckt = onlyGains(previousCounts, current);
                        if (!verschluckt.isEmpty()) {
                            ShokiMod.LOGGER.info("[Profit] arrived while settling, not counted: {}", verschluckt);
                        }
                    }
                    // Abgaenge aus dieser Sekunde zaehlen weiter mit: Wer in ihr wegwirft
                    // und danach aufhebt, hat nichts gefunden
                    long jetzt = System.currentTimeMillis();
                    for (Map.Entry<String, Integer> entry : onlyGains(current, previousCounts).entrySet()) {
                        losses.add(new Loss(entry.getKey(), entry.getValue(), jetzt, false));
                    }
                }
                swapPending = false;
                previousCounts = current;
                previousSignature = signature(client);
            }
            return;
        }

        // Der billige Teil zuerst: hat sich ueberhaupt ein Platz geruehrt? Zwanzigmal je
        // Sekunde lautet die Antwort fast immer nein, und dann faellt alles Weitere weg
        int signature = signature(client);
        if (signature == previousSignature) return;
        previousSignature = signature;

        Map<String, Integer> current = counts(client);
        if (previousCounts == null) {
            previousCounts = current;
            return;
        }

        refreshFloorLosses(client, System.currentTimeMillis());
        Map<String, Integer> gains = diff(previousCounts, current);
        previousCounts = current;
        // Beute bei offenem Fenster: der Vergleichspunkt des Fensters wandert mit.
        // Sonst stuende dasselbe beim Schliessen noch einmal als "durchs Fenster" da -
        // und waere als schon verbucht vorgemerkt, obwohl es gerade gezaehlt wurde
        if (windowOpen) windowBaseline = current;
        if (!gains.isEmpty()) held.add(new Held(gains, tickCount));
    }

    /**
     * Was hereinkam, waehrend ein Fenster offen war - und deshalb nicht zaehlt.
     *
     * Steht nur im Log, damit die Frage "warum steht das nicht im Kasten?" eine
     * Antwort hat. Truhe, Basar, Auktionshaus, Sack und Craft laufen alle ueber ein
     * Fenster, und nichts davon ist ein Fund.
     */
    /** Die reinen Zugaenge zwischen zwei Staenden - ohne Verrechnung, nur zum Hinsehen */
    private static Map<String, Integer> onlyGains(Map<String, Integer> before, Map<String, Integer> after) {
        Map<String, Integer> gains = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : after.entrySet()) {
            int delta = entry.getValue() - before.getOrDefault(entry.getKey(), 0);
            if (delta > 0) gains.put(entry.getKey(), delta);
        }
        return gains;
    }

    private static void noteWindow(Map<String, Integer> before, Map<String, Integer> after) {
        Map<String, Integer> gains = new LinkedHashMap<>();
        long now = System.currentTimeMillis();
        boolean afterSwap = now - lastSwapAt < SWAP_WINDOW_MILLIS;
        for (Map.Entry<String, Integer> entry : after.entrySet()) {
            int delta = entry.getValue() - before.getOrDefault(entry.getKey(), 0);
            if (delta <= 0) continue;

            // Gleich nach einem Loadout-Wechsel kommt durch das Fenster nur Ruestung herein -
            // alles andere ist ein Fund, der in diese Sekunde fiel, und zaehlt
            if (afterSwap && !isGear(entry.getKey())) {
                ShokiMod.LOGGER.info("[Profit] arrived next to a loadout swap, counted: {}={}", entry.getKey(), delta);
                held.add(new Held(new LinkedHashMap<>(Map.of(entry.getKey(), delta)), tickCount));
                continue;
            }
            gains.put(entry.getKey(), delta);
            // Vorgemerkt, nicht nur uebergangen: Gekauftes und Gecraftetes wandert von
            // selbst in die Saecke, und deren Sammelmeldung kaeme sonst als Fund zurueck.
            // Nur dafuer - einem spaeteren Fund derselben Ware im Inventar darf ein
            // Einkauf nicht im Weg stehen
            losses.add(new Loss(entry.getKey(), delta, now, true));
        }
        // Und was hinausging. Ohne diese Schleife war ein Wegwerfen bei offenem
        // Inventar unsichtbar: Das Fenster hielt den Vergleich an, beim Schliessen wurde
        // neu geeicht, und das Wiederaufheben kam als Fund herein. Genau so sind drei
        // Moby-Ducks in den Kasten gewandert, die einer waren
        for (Map.Entry<String, Integer> entry : before.entrySet()) {
            int delta = entry.getValue() - after.getOrDefault(entry.getKey(), 0);
            if (delta > 0) losses.add(new Loss(entry.getKey(), delta, now, false));
        }

        if (!gains.isEmpty()) {
            ShokiMod.LOGGER.info("[Profit] came in through a window, not counted: {}", gains);
        }
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
     * Mit Ruestung und Zweithand, ohne das SkyBlock-Menue (dessen Platz sich staendig
     * aendert), aber mit dem, was am Mauszeiger haengt: das liegt in keinem Platz und
     * waere sonst ein Abgang.
     */
    private static int signature(Minecraft client) {
        List<ItemStack> items = client.player.getInventory().getNonEquipmentItems();
        int hash = 1;
        for (int slot = 0; slot < items.size(); slot++) {
            ItemStack stack = items.get(slot);
            if (stack == null || stack.isEmpty()) continue;
            hash = hash * 31 + ItemStack.hashItemAndComponents(stack) * 31 + stack.getCount();
        }
        for (EquipmentSlot slot : WORN) {
            ItemStack worn = client.player.getItemBySlot(slot);
            if (worn == null || worn.isEmpty()) continue;
            hash = hash * 31 + ItemStack.hashItemAndComponents(worn) * 31 + worn.getCount();
        }
        ItemStack carried = client.player.containerMenu.getCarried();
        if (carried != null && !carried.isEmpty()) {
            hash = hash * 31 + ItemStack.hashItemAndComponents(carried) * 31 + carried.getCount();
        }
        return hash;
    }

    /**
     * Die getragenen Teile: Ruestung und Zweithand.
     *
     * Sie zaehlen mit, obwohl sie beim Spielen stillstehen - denn beim Umziehen stehen
     * sie eben nicht still. Ein Helm, der vom Kopf ins Inventar wandert, waere sonst ein
     * Fund: Er taucht im Inventar auf, und wo er herkommt, sah der Vergleich nicht.
     *
     * Die Haupthand fehlt mit Absicht - sie ist ein Platz der Hotbar und steckt schon im
     * Inventar. Zweimal gezaehlt waere sie ein Fund bei jedem Wechsel der Waffe.
     */
    private static final EquipmentSlot[] WORN = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
            EquipmentSlot.FEET, EquipmentSlot.OFFHAND};

    /** Wie viel von welcher Ware der Spieler gerade bei sich hat */
    private static Map<String, Integer> counts(Minecraft client) {
        Map<String, Integer> out = new LinkedHashMap<>();
        List<ItemStack> items = client.player.getInventory().getNonEquipmentItems();
        for (int slot = 0; slot < items.size(); slot++) {
            if (slot == DISPLAY_SLOT) continue;
            add(out, items.get(slot));
        }
        long jetzt = System.currentTimeMillis();
        for (EquipmentSlot slot : WORN) {
            ItemStack getragen = client.player.getItemBySlot(slot);
            add(out, getragen);
            // Mitschreiben, was am Koerper haengt: Was von dort ins Inventar wandert,
            // ist ein Umzug - auch wenn der Server sich damit eine Sekunde Zeit laesst.
            // Nur die vier Ruestungsplaetze: In der Zweithand steckt mal ein Koeder oder
            // ein Block, und davon findet man welche. Ein Helm ist ein Helm
            if (slot == EquipmentSlot.OFFHAND) continue;
            String id = SkyBlockItems.idOf(getragen);
            if (id != null) worn.put(id, jetzt);
        }
        add(out, client.player.containerMenu.getCarried());
        return out;
    }

    /**
     * Was zuletzt am Koerper hing, mit dem Zeitpunkt.
     *
     * Der Vergleich sieht nur Staende, keine Wege. Taucht ein Helm im Inventar auf, der
     * im selben Augenblick noch im Ruestungsplatz steckt, hat der Server den Umzug in
     * zwei Pakete zerlegt und das erste ist schon da - im Log vom 01.10. lagen zwischen
     * beiden Haelften eine ganze Sekunde, weil nebenbei Pest-Fallen geleert und Mobs
     * gekillt wurden. Drei Ticks Wartezeit reichen dafuer nicht; die Frage "hing das
     * gerade noch an mir?" schon.
     */
    private static final Map<String, Long> worn = new HashMap<>();
    /** So lange gilt ein abgelegtes Teil noch als Umzug und nicht als Fund */
    private static final long WORN_MILLIS = 10_000L;

    /** Hing diese Ware eben noch am Koerper? */
    private static boolean wasWorn(String itemId, long now) {
        Long zuletzt = worn.get(itemId);
        if (zuletzt == null) return false;
        if (now - zuletzt > WORN_MILLIS) {
            worn.remove(itemId);
            return false;
        }
        return true;
    }

    private static void add(Map<String, Integer> counts, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        String id = SkyBlockItems.idOf(stack);
        if (id == null || MENU_ID.equals(id)) return;
        counts.merge(id, stack.getCount(), Integer::sum);
        if (!gearById.containsKey(id)) gearById.put(id, looksLikeGear(stack));
        // Nur beim ersten Mal: den Namen auseinanderzunehmen lohnt sich nicht in jedem Tick
        if (!colours.containsKey(id)) {
            int colour = SkyBlockItems.nameColour(stack);
            if (colour != 0) colours.put(id, colour);
        }
    }

    /** Die beobachtete Namensfarbe einer Ware, oder 0 */
    public static int colourOf(String itemId) {
        Integer colour = colours.get(itemId);
        return colour == null ? 0 : colour;
    }

    /**
     * Was lange genug gewartet hat, zaehlt jetzt - was inzwischen wieder wegging, nicht.
     *
     * In der Wartezeit kann ein Abgang nachkommen, der den Zugang erklaert: das
     * Ruestungsteil, das aus seinem Platz verschwindet, nachdem es im Inventar auftauchte.
     * Genau dafuer wird hier ein zweites Mal gegengerechnet.
     */
    private static void flushHeld() {
        if (held.isEmpty()) return;

        long now = System.currentTimeMillis();
        for (int i = 0; i < held.size(); ) {
            Held warte = held.get(i);
            if (tickCount - warte.tick() < HOLD_TICKS) {
                i++;
                continue;
            }
            held.remove(i);

            Map<String, Integer> rest = new LinkedHashMap<>();
            for (Map.Entry<String, Integer> entry : warte.gains().entrySet()) {
                if (wasWorn(entry.getKey(), now)) {
                    ShokiMod.LOGGER.info("[Profit] {} came off the body, not a find", entry.getKey());
                    continue;
                }
                int uebrig = offset(entry.getKey(), entry.getValue(), OFFSET_MILLIS, now, false);
                if (uebrig > 0) rest.put(entry.getKey(), uebrig);
            }
            if (!rest.isEmpty()) dispatch(rest, "inventory");
        }
    }

    /** Zugaenge zwischen zwei Staenden, verrechnet mit den Abgaengen der letzten Sekunden */
    private static Map<String, Integer> diff(Map<String, Integer> before, Map<String, Integer> after) {
        long now = System.currentTimeMillis();
        expireLosses(now);

        Map<String, Integer> gains = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : after.entrySet()) {
            int delta = entry.getValue() - before.getOrDefault(entry.getKey(), 0);
            if (delta <= 0) continue;

            int rest = offset(entry.getKey(), delta, OFFSET_MILLIS, now, false);
            if (rest > 0) gains.put(entry.getKey(), rest);
        }
        // Was verschwunden ist, bleibt vorgemerkt. Zwei Faelle laufen darueber: wer
        // wegwirft und wieder aufhebt, hat nichts gefunden - und was in einen Sack
        // wandert, verschwindet hier und taucht gleich darauf in der Sack-Meldung
        // wieder auf
        for (Map.Entry<String, Integer> entry : before.entrySet()) {
            int delta = entry.getValue() - after.getOrDefault(entry.getKey(), 0);
            if (delta > 0) losses.add(new Loss(entry.getKey(), delta, now, false));
        }
        return gains;
    }

    /**
     * Rechnet einen Zugang gegen vorgemerkte Abgaenge auf und gibt zurueck, was
     * uebrig bleibt.
     *
     * Aelteste zuerst, damit die kurzlebigen Posten zuerst verbraucht werden. Was
     * ausserhalb des Fensters liegt, bleibt liegen statt verworfen zu werden - das
     * Fenster der Sack-Meldung ist laenger als das des Wiederaufhebens.
     */
    private static int offset(String itemId, int amount, long window, long now, boolean forSack) {
        int rest = amount;
        for (int i = 0; i < losses.size() && rest > 0; i++) {
            Loss loss = losses.get(i);
            if (!loss.itemId.equals(itemId) || now - loss.at > window) continue;
            // Was nur fuer die Saecke vorgemerkt ist, geht das Inventar nichts an
            if (loss.sackOnly && !forSack) continue;

            int used = Math.min(loss.amount, rest);
            loss.amount -= used;
            rest -= used;
        }
        losses.removeIf(loss -> loss.amount <= 0);
        if (rest < amount && debug()) {
            ShokiMod.LOGGER.info("[Profit] {} x{} already booked, {} left over", itemId, amount, rest);
        }
        return rest;
    }

    private static void expireLosses(long now) {
        if (losses.isEmpty()) return;
        losses.removeIf(loss -> now - loss.at > SACK_OFFSET_MILLIS);
    }

    /**
     * Ein Abgang bleibt vorgemerkt, solange sein Stueck noch daliegt.
     *
     * Ein Fenster von zehn Sekunden ist eine Wette darauf, wie schnell jemand sein
     * Zeug wieder aufhebt. Wer es hinlegt, herumlaeuft und in einer Minute zurueckkommt,
     * verliert die Wette - und der Kasten zaehlt dasselbe Stueck ein zweites Mal. Die
     * Frage ist aber gar keine Zeitfrage: Solange das Stueck in Sichtweite auf dem
     * Boden liegt, ist sein Wiederaufheben kein Fund. Also wird der Zeitpunkt
     * nachgezogen, so lange es dort liegt.
     *
     * Der Preis dafuer ist klein und benannt: Liegt eine Ware unaufgehoben herum und man
     * findet dieselbe noch einmal, geht der neue Fund gegen den alten Posten. Das ist
     * seltener als der Fall, den es behebt, und in der Richtung, die nichts erfindet.
     */
    private static void refreshFloorLosses(Minecraft client, long now) {
        if (losses.isEmpty() || client.level == null || client.player == null) return;

        Set<String> onFloor = null;
        for (int i = 0; i < losses.size(); i++) {
            Loss loss = losses.get(i);
            // Frische Posten brauchen es nicht, Sack-Posten geht der Boden nichts an
            if (loss.sackOnly || now - loss.at <= OFFSET_MILLIS) continue;

            if (onFloor == null) onFloor = floorIds(client);
            if (onFloor.isEmpty()) return;
            if (onFloor.contains(loss.itemId)) loss.at = now;
        }
    }

    /** Was in der Naehe auf dem Boden liegt */
    private static Set<String> floorIds(Minecraft client) {
        Set<String> out = new java.util.HashSet<>();
        double reach = FLOOR_REACH * FLOOR_REACH;
        net.minecraft.world.phys.Vec3 eye = client.player.position();

        for (net.minecraft.world.entity.Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof net.minecraft.world.entity.item.ItemEntity item)) continue;
            if (entity.position().distanceToSqr(eye) > reach) continue;

            String id = SkyBlockItems.idOf(item.getItem());
            if (id != null) out.add(id);
        }
        return out;
    }

    /**
     * Die beiden Meldungen ueber Zugaenge, die das Inventar nie zu sehen bekommt.
     *
     * @param message   die Nachricht samt Mauszeiger-Text
     * @param formatted dieselbe Zeile mit den Farbcodes
     * @param plain     dieselbe Zeile ohne
     */
    public static void onChatMessage(Component message, String formatted, String plain) {
        if (message == null || plain == null) return;
        if (!FeatureGate.itemChanges() || !GameState.Server.isSkyblock()) return;

        // Die Craft-Meldung zuerst, und ohne Ruecksicht auf offene Fenster: gecraftet
        // wird in einem, und die Meldung ist der einzige Hinweis darauf, dass die Ware
        // gebaut und nicht gefunden wurde
        if (supercraft(plain)) return;
        // Und aus demselben Grund die Zeile, die einen Umzug aus dem Sack meldet
        if (fromSacks(plain)) return;
        // Ein gemeldeter Fund ist sicher gefallen - gemerkt, falls der Vergleich ihn verpasst
        noteDropLine(plain);

        // Ein Loadout-Wechsel legt eine ganze Ruestung um - das ist kein Fund
        if (EQUIPPED.matcher(plain.trim()).matches()) {
            settleTicks = SETTLE_TICKS;
            settleAfterWindow = false;
            // Der Vergleichsstand bleibt: In der Sekunde danach fallen nur die Ruestungsteile
            // aus der Zaehlung, alles andere zaehlt am Ende der Wartezeit ganz normal
            swapPending = true;
            lastSwapAt = System.currentTimeMillis();
            for (int i = 0; i < held.size(); i++) {
                held.set(i, new Held(withoutGear(held.get(i).gains()), held.get(i).tick()));
            }
            held.removeIf(h -> h.gains().isEmpty());
            ShokiMod.LOGGER.info("[Profit] armour swapped - armour and equipment of the next second do not count");
            return;
        }

        // Ein geleerter Stash schuettet seinen ganzen Inhalt in die Saecke
        if (STASH_TO_SACKS.matcher(plain.trim()).matches()) {
            stashUntil = System.currentTimeMillis() + STASH_MILLIS;
            ShokiMod.LOGGER.info("[Profit] stash emptied into the sacks - sack messages do not count for {}s",
                    STASH_MILLIS / 1000);
            return;
        }

        // Gekaufte und zurueckgegebene Ware ist keine gefundene
        if (bazaarGoods(plain)) return;

        // Teilt der Server gerade Beute aus, zaehlt sie auch bei offenem Fenster
        if (KILL_REWARD.matcher(plain.trim()).matches()) {
            lootUntil = System.currentTimeMillis() + LOOT_MILLIS;
            // Und eine laufende Wartezeit nach einem Fenster ist damit hinfaellig.
            // Im Log vom 25.09. um 19:24:43 lag genau dort der Fehler: Falle geleert,
            // Fenster zu, Wartezeit an - und die vier Buecher, die eine halbe Sekunde
            // spaeter ankamen, verschwanden in ihr. Ohne Neuansetzen abbrechen, sonst
            // waere der Vergleichspunkt schon hinter den Buechern
            if (settleAfterWindow && settleTicks > 0) {
                settleTicks = 0;
                settleAfterWindow = false;
            }
        }

        if (plain.contains(SACK_MARKER)) {
            // Der Schwung aus dem Stash laeuft ueber genau diese Meldung
            if (System.currentTimeMillis() < stashUntil) {
                ShokiMod.LOGGER.info("[Profit] sack message skipped, it belongs to the stash");
                return;
            }
            // Wer von Hand einlagert, hat den Sack offen - das ist kein Fund, sondern
            // ein Umzug. Waehrend Beute fliesst aber schon: wer seine Fallen leert,
            // steht dabei im Fallen-Menue, und die Ernte geht trotzdem in die Saecke
            boolean umzug = Minecraft.getInstance().screen instanceof AbstractContainerScreen<?>
                    && System.currentTimeMillis() >= lootUntil;
            if (!umzug) sacks(message);
            return;
        }

        // Ein gefangener Shard zaehlt immer.
        //
        // Die Zeile "CHARM! ..." ist keine Frage des Bildschirms - sie ist die einzige
        // Meldung, die es zu dem Fang gibt. Bisher hing sie an derselben Bedingung wie
        // die Sack-Zeile, und wer beim Leeren der Fallen einen Shard fing, verlor ihn:
        // im Log vom 25.09. um 13:59:37 zwei Lunar-Moth-Shards, die der Fund-Alarm
        // gemeldet hat und der Kasten nie zu sehen bekam
        shards(formatted, plain);
    }

    /**
     * Was gecraftet wurde, ist kein Fund.
     *
     * Gebaut wird in einem Fenster, und was dabei ins Inventar kommt, zaehlt schon
     * deshalb nicht. Von dort wandert es aber weiter in einen Sack, und dessen
     * Sammelmeldung kommt Sekunden spaeter - dann steht das Fenster laengst offen
     * oder zu, und die Meldung sieht aus wie ein Fund. Deshalb wird die Menge hier
     * vorgemerkt und gegen die Sack-Meldung aufgerechnet.
     *
     * @return ob die Zeile eine Craft-Meldung war
     */
    private static boolean supercraft(String plain) {
        Matcher matcher = SUPERCRAFT.matcher(plain.trim());
        if (!matcher.matches()) return false;

        String name = matcher.group("item").trim();
        int jeZeile = matcher.group("amount") == null ? 1 : number(matcher.group("amount"));
        int wiederholt = matcher.group("repeat") == null ? 1 : number(matcher.group("repeat"));
        if (name.isEmpty() || jeZeile <= 0 || wiederholt <= 0) return true;

        List<String> ids = ItemNames.idsFor(name);
        if (ids.isEmpty()) {
            // Ohne Kennung laesst sich nichts vormerken, und das Gebaute stuende als
            // Fund im Kasten - also wenigstens nachlesbar, warum
            ShokiMod.LOGGER.warn("[Profit] crafted, but no id for \"{}\"", name);
            return true;
        }
        String id = ids.get(0);
        long jetzt = System.currentTimeMillis();

        // Der Chat zaehlt Wiederholungen hoch: erst "...!", dann "...! (2)", "...! (3)".
        // Jede dieser Zeilen kommt hier an, und jede nennt die Gesamtzahl seit der
        // ersten - vorgemerkt wird deshalb nur, was seit der letzten dazugekommen ist.
        // Sonst waeren aus vierzehn Crafts hundertfuenf geworden
        int neu = wiederholt;
        if (id.equals(lastCraft) && wiederholt > lastCraftRepeat
                && jetzt - lastCraftMillis < REPEAT_MILLIS) {
            neu = wiederholt - lastCraftRepeat;
        }
        lastCraft = id;
        lastCraftRepeat = wiederholt;
        lastCraftMillis = jetzt;

        int amount = jeZeile * neu;
        // Gecraftetes ist nur fuer die Sack-Meldung vorgemerkt - im Inventar liegt es
        // schon, und was danach dort ankommt, ist wieder ein Fund
        losses.add(new Loss(id, amount, jetzt, true));
        ShokiMod.LOGGER.info("[Profit] crafted, not found: {} x{}", id, amount);
        return true;
    }

    /**
     * Ware aus einem Sack ist kein Fund.
     *
     * Sie war schon gezaehlt, als sie in den Sack fiel. Im Inventar taucht sie gleich
     * darauf wieder auf - fuer den Vergleich sieht das aus wie ein Zugang. Also wird die
     * Menge vorgemerkt und gegen den naechsten Zugang derselben Ware aufgerechnet.
     *
     * @return ob die Zeile ein solcher Umzug war
     */
    private static boolean fromSacks(String plain) {
        Matcher matcher = FROM_SACKS.matcher(plain.trim());
        if (!matcher.matches()) return false;

        String name = matcher.group("item").trim();
        int amount = number(matcher.group("amount"));
        if (name.isEmpty() || amount <= 0) return true;

        List<String> ids = ItemNames.idsFor(name);
        if (ids.isEmpty()) {
            ShokiMod.LOGGER.info("[Profit] out of the sack, but the name is unknown: {} x{}", name, amount);
            return true;
        }

        // Nicht nur fuer die Sack-Meldung: Hier kommt die Ware ins Inventar, und genau
        // dort muss sie wieder abgezogen werden
        movedOutAt.put(ids.get(0), System.currentTimeMillis());
        losses.add(new Loss(ids.get(0), amount, System.currentTimeMillis(), false));
        ShokiMod.LOGGER.info("[Profit] out of the sack, not found: {} x{}", ids.get(0), amount);
        return true;
    }

    /**
     * Ware vom Basar ist kein Fund.
     *
     * Zwei Wege fuehren von dort ins Inventar: eine eingeloeste Kauforder und eine
     * zurueckgezogene Verkaufsorder. Gekauft ist gekauft, und zurueckgegeben war nie weg -
     * beides sieht im Inventar aus wie alles andere. Die Menge steht in der Zeile, also
     * wird genau sie vorgemerkt und gegen den naechsten Zugang derselben Ware
     * aufgerechnet.
     *
     * @return ob die Zeile eine solche Meldung war
     */
    private static boolean bazaarGoods(String plain) {
        String zeile = plain.trim();
        Matcher matcher = BAZAAR_CLAIM.matcher(zeile);
        boolean gekauft = matcher.matches();
        if (!gekauft) {
            matcher = BAZAAR_REFUND.matcher(zeile);
            if (!matcher.matches()) return false;
        }

        String name = matcher.group("item").trim();
        int amount = number(matcher.group("amount"));
        if (name.isEmpty() || amount <= 0) return true;

        List<String> ids = ItemNames.idsFor(name);
        if (ids.isEmpty()) {
            ShokiMod.LOGGER.info("[Profit] from the bazaar, but the name is unknown: {} x{}", name, amount);
            return true;
        }

        losses.add(new Loss(ids.get(0), amount, System.currentTimeMillis(), false));
        ShokiMod.LOGGER.info("[Profit] {}, not found: {} x{}", gekauft ? "bought" : "refunded",
                ids.get(0), amount);
        return true;
    }

    /**
     * Ein gefangener Shard.
     *
     * Shards wandern beim Fang direkt in die Hunting Box; im Inventar taucht nie
     * einer auf, und einen Sack haben sie auch nicht. Bliebe es beim Nachzaehlen,
     * fehlte im Kasten ausgerechnet das, wonach beim Jagen gesucht wird.
     *
     * Gelesen wird dieselbe Zeile wie beim Hunting Tracker, mit demselben Parser -
     * und mit derselben Vorsicht: Zeilen aus Party- und Gildenchat erzaehlen von
     * fremden Funden.
     */
    private static void shards(String formatted, String plain) {
        String clean = plain.trim();
        for (int i = 0; i < FOREIGN_PREFIXES.length; i++) {
            if (clean.startsWith(FOREIGN_PREFIXES[i])) return;
        }

        Drop drop = RareLootParser.parse(clean);
        if (drop == null) return;

        String shard = null;
        List<String> candidates = drop.itemIdCandidates();
        for (int i = 0; i < candidates.size(); i++) {
            String candidate = candidates.get(i);
            if (candidate != null && candidate.startsWith(SHARD_PREFIX)) {
                // "Wither Spectre" heisst im Basar SHARD_WITHER_SPECTER
                shard = ItemValue.canonicalShard(candidate);
                break;
            }
        }
        if (shard == null) return;

        int amount = Math.max(1, drop.amount());
        // Ist die Box voll, faellt der Shard doch ins Inventar. Dann steht er hier
        // schon verbucht und zaehlt beim Nachzaehlen nicht noch einmal
        losses.add(new Loss(shard, amount, System.currentTimeMillis(), false));

        // Die Seltenheit steht in der Farbe des Namens - fuer Shards die einzige
        // Gelegenheit, sie ueberhaupt zu erfahren
        if (!colours.containsKey(shard)) {
            String name = drop.displayName();
            if (name.endsWith(" Shard")) name = name.substring(0, name.length() - " Shard".length());
            int colour = SkyBlockItems.colourInLine(formatted, name);
            if (colour != 0) colours.put(shard, colour);
        }

        Map<String, Integer> gains = new LinkedHashMap<>();
        gains.put(shard, amount);
        dispatch(gains, "shard catch");
    }

    /**
     * Die Sammelzeile der Saecke.
     *
     * Was direkt in einen Sack faellt, kommt nie im Inventar an. Die Zeile
     * "[Sacks] +38 items." traegt am Mauszeiger die Aufstellung - dieselbe Quelle,
     * aus der auch der Collection-Tracker liest.
     */
    private static void sacks(Component message) {
        List<String> blocks = hoverBlocks(message);
        if (blocks.isEmpty()) return;

        Map<String, Integer> gains = new LinkedHashMap<>();
        Map<String, Integer> consumed = new LinkedHashMap<>();
        for (String block : blocks) {
            // Jede Aufstellung fuer sich: die Ueberschrift "Added items:" gilt nur
            // innerhalb ihrer eigenen, nicht bis in die naechste hinein
            boolean adding = false;
            boolean removing = false;
            for (String line : block.split("\n")) {
                String clean = line.trim();
                String lower = clean.toLowerCase(Locale.ROOT);
                if (lower.startsWith("added items")) {
                    adding = true;
                    removing = false;
                    continue;
                }
                if (lower.startsWith("removed items")) {
                    adding = false;
                    removing = true;
                    continue;
                }
                if (removing) {
                    noteConsumed(clean, consumed);
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
        }

        // Der eigentliche Punkt dieser Verrechnung: ein gefangener Fisch landet erst
        // im Inventar und wandert von dort in den Sack. Beides wird gesehen - der
        // Fang als Zugang, der Umzug als Abgang. Ohne den Ausgleich stuende der Fisch
        // zweimal im Kasten, einmal vom Nachzaehlen und einmal von der Meldung.
        // Was ohne Umweg in den Sack faellt, hat keinen Abgang und zaehlt voll
        long now = System.currentTimeMillis();
        expireLosses(now);
        Map<String, Integer> net = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : gains.entrySet()) {
            int rest = offset(entry.getKey(), entry.getValue(), SACK_OFFSET_MILLIS, now, true);
            if (rest > 0) net.put(entry.getKey(), rest);
        }

        if (!net.isEmpty()) dispatch(net, "sacks");
        subtractConsumed(consumed, now);
    }

    /** Wann zuletzt etwas dieser Ware aus einem Sack ins Inventar geholt wurde */
    private static final Map<String, Long> movedOutAt = new HashMap<>();
    /** So lange gilt ein Abgang aus dem Sack als Umzug ins Inventar, nicht als Verbrauch */
    private static final long MOVE_MILLIS = 15_000L;

    /**
     * Eine Zeile unter "Removed items" - gemerkt wird sie nur bei Koedern.
     *
     * Was aus einem Sack verschwindet, ist meistens kein Verlust: Es wandert ins
     * Inventar, wird gebaut oder verkauft, und in allen drei Faellen gibt es eine eigene
     * Meldung, die es verrechnet. Beim Koeder gibt es keine - er wird beim Angeln
     * direkt aus dem Sack verbraucht, und die Sack-Meldung ist das einzige Zeichen
     * davon. Gezaehlt wurde er beim Hereinfallen, abgezogen wurde nie.
     *
     * Alle Koeder enden auf _BAIT (BLESSED, WHALE, CARROT, GLOWY_CHUM ...); Bait Ring und
     * die Koedersaecke gehoeren nicht dazu.
     */
    private static void noteConsumed(String line, Map<String, Integer> consumed) {
        Matcher matcher = SACK_LINE.matcher(line);
        if (!matcher.find()) return;
        int amount = number(matcher.group(2));
        if (amount <= 0) return;

        String name = TRAILING_SYMBOLS.matcher(LEADING_SYMBOLS.matcher(matcher.group(3))
                .replaceAll("")).replaceAll("").trim();
        if (name.isEmpty()) return;
        List<String> ids = ItemNames.idsFor(name);
        if (ids.isEmpty() || !ids.get(0).endsWith("_BAIT")) return;
        consumed.merge(ids.get(0), amount, Integer::sum);
    }

    /**
     * Den verbrauchten Koeder aus dem Kasten nehmen.
     *
     * Ausser er wurde gerade ins Inventar geholt: Dann steht der Abgang aus dem Sack
     * in derselben Meldung, und die Ware ist nicht weg, sondern woanders. Abgezogen
     * wuerde sie doppelt - einmal hier, einmal als Zugang im Inventar, den die
     * "Moved ... from your Sacks"-Zeile schon aufgerechnet hat.
     */
    private static void subtractConsumed(Map<String, Integer> consumed, long now) {
        if (consumed.isEmpty() || !com.shokiteufel.shokimod.handler.ProfitTracker.enabled()) return;
        for (Map.Entry<String, Integer> entry : consumed.entrySet()) {
            Long geholt = movedOutAt.get(entry.getKey());
            if (geholt != null && now - geholt < MOVE_MILLIS) {
                ShokiMod.LOGGER.info("[Profit] {} left the sack, but it was moved - not consumed",
                        entry.getKey());
                continue;
            }
            com.shokiteufel.shokimod.handler.ProfitTracker.adjust(entry.getKey(), -entry.getValue());
            ShokiMod.LOGGER.info("[Profit] bait used from the sack: {} -{}", entry.getKey(), entry.getValue());
        }
    }

    private static int number(String text) {
        try {
            return Integer.parseInt(text.replace(",", "").replace(".", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Der Text am Mauszeiger, Zeile fuer Zeile - jede Aufstellung aber nur einmal.
     *
     * Der Mauszeiger-Text haengt am Stil einer Nachricht, und den erben ihre Teile.
     * Wer den Baum abgeht, bekommt dieselbe Aufstellung deshalb so oft zurueck, wie
     * die Zeile Teile hat. Genau das hat jeden Posten aus einem Sack doppelt gezaehlt:
     * "+3 Raw Cod" stand zweimal da, und beide Male wurde gebucht. Skysoft sortiert an
     * derselben Stelle aus, und aus demselben Grund.
     */
    private static List<String> hoverBlocks(Component message) {
        LinkedHashSet<String> blocks = new LinkedHashSet<>();
        collectHoverText(message, blocks);
        return new ArrayList<>(blocks);
    }

    /** Jede Aufstellung einmal - verglichen wird ohne Farbcodes, sonst zaehlt dieselbe zweimal */
    private static void collectHoverText(Component component, LinkedHashSet<String> out) {
        HoverEvent hover = component.getStyle().getHoverEvent();
        if (hover instanceof HoverEvent.ShowText showText) {
            String block = showText.value().getString();
            if (!block.isBlank()) out.add(COLOUR_CODE.matcher(block).replaceAll(""));
        }
        for (Component sibling : component.getSiblings()) collectHoverText(sibling, out);
    }

    /**
     * Woher der letzte Schwung Zugaenge kam.
     *
     * Steht im Log neben dem ersten Fund einer Ware. Wenn etwas im Kasten steht, das
     * dort nicht hingehoert, ist das die erste Frage - und ohne diese Notiz liesse sie
     * sich nur raten.
     */
    private static String lastSource = "inventory";

    public static String lastSource() {
        return lastSource;
    }

    /** Der Schalter unter Profit -> Diagnostics. Aus kostet er nichts */
    private static boolean debug() {
        return ModConfig.INSTANCE.profit.debugLogging;
    }

    /**
     * Ein Fund, den der Inventar-Vergleich nie sehen kann.
     *
     * Essence liegt im Profil, nicht im Inventar; sie kommt aus der Tab-Liste. Damit
     * Kasten, Schacht-Bilanz und Alarm trotzdem davon erfahren, geht sie denselben Weg
     * wie alles andere - ein zweiter Melde-Weg waere ein zweiter Ort, an dem man
     * suchen muss, wenn etwas fehlt.
     */
    public static void report(Map<String, Integer> gains, String source) {
        if (gains == null || gains.isEmpty()) return;
        dispatch(gains, source);
    }

    private static void dispatch(Map<String, Integer> gains, String source) {
        long gezaehlt = System.currentTimeMillis();
        for (String id : gains.keySet()) recentlyCounted.put(id, gezaehlt);
        lastSource = source;
        if (debug()) ShokiMod.LOGGER.info("[Profit] +{} via {}", gains, source);
        for (int i = 0; i < listeners.size(); i++) {
            listeners.get(i).accept(gains);
        }
    }

    // ------------------------------------------------------------------ Rettung

    /** Ein in den Chat gemeldeter Fund, der noch auf sein Inventar wartet */
    private record Confirmed(java.util.List<String> ids, String preferred, int amount, long at) {
    }

    private static final List<Confirmed> confirmed = new ArrayList<>();
    /** Was zuletzt gezaehlt wurde, je Kennung - von allen Wegen, die etwas melden */
    private static final Map<String, Long> recentlyCounted = new HashMap<>();
    /**
     * So lange hat das Inventar Zeit, den Fund selbst zu zaehlen.
     *
     * Im Normalfall tut es das in derselben Sekunde, und dann geschieht hier nichts. Die
     * Frist ist laenger als der Vergleich braucht, und kuerzer als alles, was danach
     * noch zaehlen koennte.
     */
    private static final long CONFIRM_WAIT_MILLIS = 3_000L;

    /**
     * Eine Zeile "RARE DROP! ..." als Beweis merken.
     *
     * Steht sie im Chat, ist der Fund gefallen - ganz gleich, was das Inventar dazu
     * sagt. Es sagt nicht immer etwas: Wer im selben Augenblick sein Loadout wechselt,
     * hat eine Sekunde, in der nichts zaehlt, und die Warteschlange gegen Ruestungswechsel
     * wird geleert. Ein Flash-Buch fiel genau in diese Sekunde und fehlte im Kasten.
     */
    private static void noteDropLine(String plain) {
        String clean = plain.trim();
        if (com.shokiteufel.shokimod.handler.RareLootHandler.isChatLine(clean)) return;
        com.shokiteufel.shokimod.data.RareLootParser.Drop drop = com.shokiteufel.shokimod.data.RareLootParser.parse(clean);
        // Pet-Zeilen immer festhalten: Ihre genaue Form ist die Unbekannte, und ohne diese
        // Zeile im Log laesst sich nicht sagen, warum ein Pet fehlte
        if (clean.toUpperCase(java.util.Locale.ROOT).contains("PET DROP")) {
            ShokiMod.LOGGER.info("[Profit] pet drop line: \"{}\" -> {}", clean,
                    drop == null ? "not understood" : drop.itemIdCandidates());
        }
        if (drop == null || drop.amount() <= 0) return;

        List<String> ids = com.shokiteufel.shokimod.handler.RareLootHandler.candidatesFor(drop);
        if (ids.isEmpty()) return;
        // Essence ist eine Waehrung: Sie waechst weiter, ein einzelner Fund ist keiner
        if (ids.get(0).startsWith("ESSENCE_")) return;
        // Die Preise werden gleich gebraucht, um die richtige Kennung zu waehlen
        com.shokiteufel.shokimod.util.ItemValue.prefetch();
        confirmed.add(new Confirmed(List.copyOf(ids), null, drop.amount(), System.currentTimeMillis()));
        ShokiMod.LOGGER.info("[Profit] chat says {} x{} dropped - waiting {}s for the inventory",
                ids.get(0), drop.amount(), CONFIRM_WAIT_MILLIS / 1000);
    }

    /**
     * Welche der Kennungen ist die echte?
     *
     * Die Liste nennt mehrere, weil der Name nicht verraet, was gemeint ist: "Flash I" kann
     * ENCHANTMENT_FLASH_1 sein oder ENCHANTMENT_ULTIMATE_FLASH_1. Echt ist die, fuer die
     * es einen Preis gibt - eine Kennung, die niemand handelt, gibt es auch nicht. Die
     * erste Kennung der Liste zu nehmen hiesse zu raten, und das Buch stuende danach unter
     * einem Namen im Kasten, den das Inventar nie liefert.
     */
    private static String realId(List<String> ids) {
        for (String id : ids) {
            if (com.shokiteufel.shokimod.util.ItemValue.BAZAAR.get(id) != null) return id;
        }
        for (String id : ids) {
            Double bin = com.shokiteufel.shokimod.util.ItemValue.LOWEST_BIN.get(id);
            if (bin != null && bin > 0) return id;
        }
        return ids.get(0);
    }

    /**
     * Was die Chatzeile sagte und das Inventar nicht bestaetigt hat, nachtragen.
     *
     * Erst nach der Frist, und nur, wenn in der Zwischenzeit keine der moeglichen
     * Kennungen gezaehlt wurde. So aendert sich im Normalfall nichts - der Fund ist
     * dann laengst im Kasten -, und nur der verlorene Fall wird gerettet.
     *
     * Dazu wird die Menge als bereits gebucht vermerkt. Kommt das Inventar spaeter doch
     * noch dazu - ein Fenster, das erst zugeht -, bucht es damit nicht ein zweites Mal.
     */
    private static void rescueConfirmed() {
        if (confirmed.isEmpty()) return;
        long now = System.currentTimeMillis();
        for (int i = 0; i < confirmed.size(); ) {
            Confirmed c = confirmed.get(i);
            if (now - c.at() < CONFIRM_WAIT_MILLIS) {
                i++;
                continue;
            }
            confirmed.remove(i);

            boolean schonGezaehlt = false;
            for (String id : c.ids()) {
                Long at = recentlyCounted.get(id);
                if (at != null && at >= c.at() - CONFIRM_WAIT_MILLIS) {
                    schonGezaehlt = true;
                    break;
                }
            }
            if (schonGezaehlt) continue;

            String echt = realId(c.ids());
            Map<String, Integer> nachtrag = new LinkedHashMap<>();
            nachtrag.put(echt, c.amount());
            ShokiMod.LOGGER.info("[Profit] the inventory never counted {} x{} - taking it from the chat line",
                    echt, c.amount());
            for (String id : c.ids()) losses.add(new Loss(id, c.amount(), now, false));
            dispatch(nachtrag, "drop line");
        }
    }

    /** Beim Verlassen von SkyBlock faellt der Vergleichsstand weg, nicht die Zaehlung */
    public static void reset() {
        previousCounts = null;
        previousContext = null;
        previousSignature = 0;
        settleTicks = SETTLE_TICKS;
        settleAfterWindow = false;
        windowBaseline = null;
        losses.clear();
        held.clear();
        worn.clear();
        confirmed.clear();
    }
}
