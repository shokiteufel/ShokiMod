package com.shokiteufel.shokimod.scanner;

import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.data.FeatureGate;
import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.RareLootParser;
import com.shokiteufel.shokimod.data.RareLootParser.Drop;
import com.shokiteufel.shokimod.util.ItemValue;
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
import java.util.LinkedHashSet;
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
     * Das SkyBlock-Menue zaehlt nicht mit.
     *
     * Erkannt wird es an seiner Kennung, nicht an seinem Platz. Es liegt zwar
     * gewoehnlich auf dem letzten Platz der Schnellleiste, aber man kann es
     * verschieben - und dann lag dort ein gewoehnlicher Gegenstand, dessen Zugaenge
     * niemand gezaehlt haette.
     */
    private static final String MENU_ID = "SKYBLOCK_MENU";

    private static final Pattern COLOUR_CODE = Pattern.compile("§.");
    private static final Pattern LEADING_SYMBOLS = Pattern.compile("^[^\\p{L}\\p{N}]+");
    private static final Pattern TRAILING_SYMBOLS = Pattern.compile("[^\\p{L}\\p{N}]+$");
    /** "+38 Enchanted Helix Log (Enchanted Foraging Sack)" */
    private static final Pattern SACK_LINE = Pattern.compile("^([+-])\\s*([\\d,.]+)\\s+(.+?)\\s*\\(");
    private static final String SACK_MARKER = "[Sacks]";
    /** "You Supercrafted Blessed Bait x256!" - gebaut, nicht gefunden */
    private static final Pattern SUPERCRAFT = Pattern.compile(
            "^You Supercrafted (?<item>.+?)(?: x(?<amount>[\\d,]+))?!$", Pattern.CASE_INSENSITIVE);
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
    /** Der Stand, als ein Fenster aufging - um danach zu sehen, was hindurchkam */
    private static Map<String, Integer> windowBaseline = null;
    /**
     * Abgaenge der letzten Sekunden, gegen die spaetere Zugaenge verrechnet werden.
     *
     * Jeder Posten mit eigener Uhrzeit, weil die beiden Faelle unterschiedlich lange
     * nachwirken: das Wiederaufheben von Weggeworfenem zehn Sekunden, die Sack-Meldung
     * gut eine Minute.
     */
    private static final List<Loss> losses = new ArrayList<>();

    /** Ein Abgang: so viele Stueck dieser Ware sind zu diesem Zeitpunkt verschwunden */
    private static final class Loss {
        final String itemId;
        int amount;
        final long at;

        Loss(String itemId, int amount, long at) {
            this.itemId = itemId;
            this.amount = amount;
            this.at = at;
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

        // Ein offener Behaelter ist der Weg, auf dem Gekauftes, Ausgelagertes und
        // Gecraftetes hereinkommt. Nichts davon ist ein Fund
        if (client.screen instanceof AbstractContainerScreen<?>) {
            if (windowBaseline == null) {
                windowBaseline = previousCounts == null ? Map.of() : previousCounts;
            }
            return;
        }

        String context = context(client);
        if (!context.equals(previousContext)) {
            previousContext = context;
            // Nach einem Wechsel kommt das Inventar Stueck fuer Stueck an; erst danach
            // ist ein Vergleich etwas wert
            settleTicks = SETTLE_TICKS;
            previousCounts = null;
            windowBaseline = null;
            return;
        }

        if (windowBaseline != null) {
            // Das Fenster ist zu. Der Stand von jetzt ist der neue Vergleichspunkt -
            // was durch das Fenster kam, zaehlt nicht als Fund
            Map<String, Integer> current = counts(client);
            noteWindow(windowBaseline, current);
            windowBaseline = null;
            previousCounts = current;
            previousSignature = signature(client);
            // Was beim Bauen oder Kaufen entsteht, kommt manchmal erst ein paar Ticks
            // nach dem Schliessen an. Diese Sekunde gehoert noch zum Fenster
            settleTicks = SETTLE_TICKS;
            return;
        }

        if (settleTicks > 0) {
            settleTicks--;
            if (settleTicks == 0) {
                previousCounts = counts(client);
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

        Map<String, Integer> gains = diff(previousCounts, current);
        previousCounts = current;
        if (!gains.isEmpty()) dispatch(gains, "inventory");
    }

    /**
     * Was hereinkam, waehrend ein Fenster offen war - und deshalb nicht zaehlt.
     *
     * Steht nur im Log, damit die Frage "warum steht das nicht im Kasten?" eine
     * Antwort hat. Truhe, Basar, Auktionshaus, Sack und Craft laufen alle ueber ein
     * Fenster, und nichts davon ist ein Fund.
     */
    private static void noteWindow(Map<String, Integer> before, Map<String, Integer> after) {
        Map<String, Integer> gains = new LinkedHashMap<>();
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Integer> entry : after.entrySet()) {
            int delta = entry.getValue() - before.getOrDefault(entry.getKey(), 0);
            if (delta <= 0) continue;

            gains.put(entry.getKey(), delta);
            // Vorgemerkt, nicht nur uebergangen: Gekauftes und Gecraftetes wandert von
            // selbst in die Saecke, und deren Sammelmeldung kaeme sonst als Fund zurueck
            losses.add(new Loss(entry.getKey(), delta, now));
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
     * Ohne Ruestung, ohne das SkyBlock-Menue (dessen Platz sich staendig aendert),
     * aber mit dem, was am Mauszeiger haengt: das liegt in keinem Platz und waere
     * sonst ein Abgang.
     */
    private static int signature(Minecraft client) {
        List<ItemStack> items = client.player.getInventory().getNonEquipmentItems();
        int hash = 1;
        for (int slot = 0; slot < items.size(); slot++) {
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
            add(out, items.get(slot));
        }
        add(out, client.player.containerMenu.getCarried());
        return out;
    }

    private static void add(Map<String, Integer> counts, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        String id = SkyBlockItems.idOf(stack);
        if (id == null || MENU_ID.equals(id)) return;
        counts.merge(id, stack.getCount(), Integer::sum);
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

    /** Zugaenge zwischen zwei Staenden, verrechnet mit den Abgaengen der letzten Sekunden */
    private static Map<String, Integer> diff(Map<String, Integer> before, Map<String, Integer> after) {
        long now = System.currentTimeMillis();
        expireLosses(now);

        Map<String, Integer> gains = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : after.entrySet()) {
            int delta = entry.getValue() - before.getOrDefault(entry.getKey(), 0);
            if (delta <= 0) continue;

            int rest = offset(entry.getKey(), delta, OFFSET_MILLIS, now);
            if (rest > 0) gains.put(entry.getKey(), rest);
        }
        // Was verschwunden ist, bleibt vorgemerkt. Zwei Faelle laufen darueber: wer
        // wegwirft und wieder aufhebt, hat nichts gefunden - und was in einen Sack
        // wandert, verschwindet hier und taucht gleich darauf in der Sack-Meldung
        // wieder auf
        for (Map.Entry<String, Integer> entry : before.entrySet()) {
            int delta = entry.getValue() - after.getOrDefault(entry.getKey(), 0);
            if (delta > 0) losses.add(new Loss(entry.getKey(), delta, now));
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
    private static int offset(String itemId, int amount, long window, long now) {
        int rest = amount;
        for (int i = 0; i < losses.size() && rest > 0; i++) {
            Loss loss = losses.get(i);
            if (!loss.itemId.equals(itemId) || now - loss.at > window) continue;

            int used = Math.min(loss.amount, rest);
            loss.amount -= used;
            rest -= used;
        }
        losses.removeIf(loss -> loss.amount <= 0);
        return rest;
    }

    private static void expireLosses(long now) {
        if (losses.isEmpty()) return;
        losses.removeIf(loss -> now - loss.at > SACK_OFFSET_MILLIS);
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
        // Wer von Hand einlagert oder in der Box raeumt, steht in einem Fenster - das
        // ist kein Fund, sondern ein Umzug
        if (Minecraft.getInstance().screen instanceof AbstractContainerScreen<?>) return;

        if (plain.contains(SACK_MARKER)) {
            sacks(message);
        } else {
            shards(formatted, plain);
        }
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
        int amount = matcher.group("amount") == null ? 1 : number(matcher.group("amount"));
        if (name.isEmpty() || amount <= 0) return true;

        List<String> ids = ItemNames.idsFor(name);
        if (ids.isEmpty()) return true;

        losses.add(new Loss(ids.get(0), amount, System.currentTimeMillis()));
        ShokiMod.LOGGER.info("[Profit] crafted, not found: {} x{}", ids.get(0), amount);
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
        losses.add(new Loss(shard, amount, System.currentTimeMillis()));

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
        for (String block : blocks) {
            // Jede Aufstellung fuer sich: die Ueberschrift "Added items:" gilt nur
            // innerhalb ihrer eigenen, nicht bis in die naechste hinein
            boolean adding = false;
            for (String line : block.split("\n")) {
                String clean = line.trim();
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
            int rest = offset(entry.getKey(), entry.getValue(), SACK_OFFSET_MILLIS, now);
            if (rest > 0) net.put(entry.getKey(), rest);
        }

        if (!net.isEmpty()) dispatch(net, "sacks");
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

    private static void dispatch(Map<String, Integer> gains, String source) {
        lastSource = source;
        for (int i = 0; i < listeners.size(); i++) {
            listeners.get(i).accept(gains);
        }
    }

    /** Beim Verlassen von SkyBlock faellt der Vergleichsstand weg, nicht die Zaehlung */
    public static void reset() {
        previousCounts = null;
        previousContext = null;
        previousSignature = 0;
        settleTicks = SETTLE_TICKS;
        windowBaseline = null;
        losses.clear();
    }
}
