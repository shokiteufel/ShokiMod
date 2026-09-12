package com.shokiteufel.shokimod.scanner;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Was an Shards im Lager liegt, gelesen aus der offenen Hunting-Box.
 *
 * Gemerkt wird ueber das Schliessen hinaus: Wer wissen will, welche Fusion er sich
 * jetzt leisten kann, hat die Box in dem Moment nicht offen. Der Stand ist damit so
 * frisch wie der letzte Blick hinein - und genau das sagt die Anzeige auch.
 *
 * <p><b>Warum ueber den Namen und nicht ueber die Kennung:</b> Die Fusionsdaten
 * fuehren Shards unter ihrem Namen ("Sun Fish"), und der steht im Feld sichtbar da.
 * Die SkyBlock-Kennung steckt dagegen in versteckten Daten, deren Aufbau sich mit
 * jedem Hypixel-Update aendern kann.
 *
 * <p>Die Stueckzahl kann an zwei Stellen stehen: als Stapelgroesse, oder als Zeile in
 * der Beschreibung. Gesucht wird beides, denn welche von beiden Hypixel benutzt, ist
 * von aussen nicht zu erraten - der Diagnosebericht nennt deshalb, was gefunden wurde.
 */
public final class ShardStock {

    /**
     * Die Fenster, in denen Shards liegen - so wie Hypixel sie benennt.
     *
     * Vor dem Namen kann eine Seitenangabe stehen: "(2/3) Hunting Box". Sie ist die
     * wertvollste Angabe im ganzen Fenster, denn aus ihr geht hervor, wie viele Seiten
     * es ueberhaupt gibt - und damit, ob man schon alles gesehen hat.
     */
    private static final Pattern TITLE = Pattern.compile(
            "^(?:\\((?<page>\\d+)/(?<pages>\\d+)\\)\\s*)?"
            + "(?<name>Hunting Box|Attribute Menu|Fusion Box|Oddities\\s*.\\s*Shards)\\s*$",
            Pattern.CASE_INSENSITIVE);
    /**
     * Der Name eines Shards, ohne die Stufe dahinter.
     *
     * Im Feld steht "Abyssal Miner IV" - der Name und dahinter die Stufe als roemische
     * Zahl. Wer sie stehen laesst, sucht in den Fusionsdaten nach "Abyssal Miner IV"
     * und findet nichts; genau daran scheiterten die Sorten, die nicht zugeordnet
     * werden konnten.
     */
    private static final Pattern SHARD_NAME = Pattern.compile(
            "^(?<name>.+?)\\s+(?<tier>[IVXL]+)$");
    /** "Stored: 1,234" oder "Amount: 12" - die Menge in der Beschreibung */
    private static final Pattern LORE_AMOUNT = Pattern.compile(
            "^(?:stored|amount|quantity|total)\\s*:?\\s*(?<n>[0-9][0-9,.]*)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern COLOUR_CODE = Pattern.compile("§.");
    /** Oefter als das lohnt der Blick ins offene Fenster nicht */
    private static final long GAP_MILLIS = 500L;
    /**
     * So lange gilt ein Blick als zur selben Sitzung gehoerig.
     *
     * Die Box hat mehrere Seiten. Wer blaettert, oeffnet aus Sicht des Spiels jedes
     * Mal ein neues Fenster - und wenn jede Seite die vorige ersetzt, bleibt am Ende
     * nur die letzte uebrig. Gemeldet wurden dreissig von sechzig Sorten, also genau
     * eine Seite. Innerhalb dieser Spanne wird deshalb ergaenzt statt ersetzt.
     */
    private static final long SESSION_MILLIS = 60_000L;

    private static final Map<String, Integer> counts = new HashMap<>();
    private static volatile long seenAt = 0L;
    private static volatile String lastTitle = "";
    private static volatile int lastSlots = 0;
    private static volatile String howCounted = "";
    private static volatile int pages = 0;
    private static volatile int slotsSeen = 0;
    /**
     * Was auf welcher Seite lag, beim letzten Blick dorthin.
     *
     * Der Gesamtbestand entsteht daraus; eine Seite neu zu lesen ersetzt ihren
     * Beitrag ganz. Nur so kann ein Shard auch wieder verschwinden.
     */
    private static final Map<Integer, Map<String, Integer>> perPage =
            java.util.Collections.synchronizedMap(new java.util.TreeMap<>());
    /** Wie viele Seiten die Box hat, laut ihrem eigenen Titel */
    private static volatile int totalPages = 0;
    private static long lastLook = 0L;

    private ShardStock() {
    }

    /** Der ganze Bestand, klein geschrieben - fuer den Abgleich mit den Fusionen */
    public static java.util.Map<String, Integer> counts() {
        return java.util.Collections.unmodifiableMap(counts);
    }

    /** Wie viele von diesem Shard im Lager liegen, oder 0 */
    public static int have(String shardName) {
        if (shardName == null || shardName.isBlank()) return 0;
        Integer n = counts.get(key(shardName));
        return n == null ? 0 : n;
    }

    /**
     * Steht gerade eine Shard-Box offen?
     *
     * Gefragt beim Zeichnen, also in jedem Bild - deshalb nur der Blick auf den
     * Titel des offenen Fensters und keine Zaehlerei.
     */
    public static boolean inBox() {
        Minecraft client = Minecraft.getInstance();
        if (!(client.screen instanceof AbstractContainerScreen<?> screen)) return false;
        return TITLE.matcher(clean(screen.getTitle().getString())).matches();
    }

    /** Wurde schon einmal hineingesehen? */
    public static boolean known() {
        return seenAt > 0 && !counts.isEmpty();
    }

    /** Wie viele verschiedene Shards gezaehlt wurden */
    public static int kinds() {
        return counts.size();
    }

    /** Alles vergessen - fuer den Fall, dass der Stand offensichtlich nicht mehr stimmt */
    public static void forget() {
        perPage.clear();
        counts.clear();
        totalPages = 0;
        pages = 0;
        slotsSeen = 0;
        seenAt = 0L;
    }

    /**
     * Wie viele verschiedene Seiten gelesen wurden.
     *
     * Gelesen wird nur, was im Fenster steht. Wer die Box oeffnet und nicht
     * blaettert, hat genau eine Seite - und diese Zahl sagt es ihm.
     */
    public static int pages() {
        return perPage.size();
    }

    /** Wie viele Seiten die Box hat, laut ihrem Titel. 0 heisst: unbekannt */
    public static int totalPages() {
        return totalPages;
    }

    /** Fehlt noch eine Seite? */
    public static boolean incomplete() {
        return totalPages > 1 && pages() < totalPages;
    }

    /** "Seite 2 von 3 gelesen" - oder leer, wenn die Box es nicht verraet */
    public static String pageNote() {
        if (totalPages <= 1) return "";
        return pages() + "/" + totalPages + " pages read";
    }

    /** Wie lange der letzte Blick her ist, in Worten */
    public static String age() {
        if (seenAt == 0L) return "";
        long seconds = Math.max(0, (System.currentTimeMillis() - seenAt) / 1000L);
        if (seconds < 60) return "just now";
        if (seconds < 3600) return (seconds / 60) + " minutes ago";
        long stunden = seconds / 3600;
        return stunden == 1 ? "1 hour ago" : stunden + " hours ago";
    }

    /**
     * Der Blick ins offene Fenster. Aufgerufen im Takt des Spiels.
     *
     * Gezaehlt wird in eine eigene Ablage und erst am Ende uebernommen: Ein halb
     * gelesenes Fenster - etwa waehrend Hypixel die Felder noch fuellt - wuerde sonst
     * einen Bestand melden, der gerade erst im Entstehen ist.
     */
    public static void tick(Minecraft client) {
        if (client == null || !(client.screen instanceof AbstractContainerScreen<?> screen)) return;
        long now = System.currentTimeMillis();
        if (now - lastLook < GAP_MILLIS) return;
        lastLook = now;

        String title = clean(screen.getTitle().getString());
        Matcher kopf = TITLE.matcher(title);
        if (!kopf.matches()) return;
        // Steht eine Seitenangabe im Titel, weiss die Mod, wie viel noch fehlt
        int dieseSeite = kopf.group("page") == null ? 1 : parse(kopf.group("page"));
        int seitenGesamt = kopf.group("pages") == null ? 1 : parse(kopf.group("pages"));

        // Eine Seite, die schon einmal gezaehlt wurde, darf nicht doppelt zaehlen -
        // wer zurueckblaettert, haette sonst die doppelte Menge im Lager
        Map<String, Integer> gefunden = new LinkedHashMap<>();
        int felder = 0;
        boolean ausStapel = false;
        boolean ausText = false;

        for (Slot slot : screen.getMenu().slots) {
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;
            String name = clean(stack.getHoverName().getString());
            if (name.isEmpty()) continue;
            // "Abyssal Miner IV" -> "Abyssal Miner". Die Stufe gehoert zum Zustand
            // des Shards, nicht zu seinem Namen - die Fusionsdaten kennen nur diesen
            Matcher ohneStufe = SHARD_NAME.matcher(name);
            if (ohneStufe.matches()) name = ohneStufe.group("name");
            // Die Knoepfe des Fensters tragen keine Mengen und keine Shard-Namen;
            // sie fallen beim Abgleich mit den Fusionsdaten von selbst heraus
            int menge = amountOf(stack);
            if (menge <= 0) continue;
            if (stack.getCount() > 1) ausStapel = true;
            if (menge != stack.getCount()) ausText = true;
            gefunden.merge(key(name), menge, Integer::sum);
            felder++;
        }

        if (gefunden.isEmpty()) return;

        // Innerhalb einer Sitzung wird ergaenzt, danach neu angefangen. Sonst bliebe
        // ein Lagerstand von gestern stehen, den es so laengst nicht mehr gibt
        if (now - seenAt > SESSION_MILLIS) {
            perPage.clear();
            totalPages = 0;
        }

        // Jede Seite fuer sich, und ein erneuter Blick ersetzt sie vollstaendig.
        //
        // Frueher wurde zusammengefuehrt und dabei die groessere Zahl behalten - das
        // hielt beim Blaettern alles zusammen, liess aber nie etwas verschwinden. Wer
        // seine Miner-Shards aus der Box ins Inventar legte und erneut hineinsah,
        // bekam sie weiter angeboten: Die alte Zahl war groesser als die neue Null und
        // gewann. Seitenweise ersetzt, kann ein Shard auch wieder weg sein.
        perPage.put(dieseSeite, gefunden);
        if (seitenGesamt > 0) totalPages = seitenGesamt;

        // Der Gesamtbestand ist die Summe der Seiten. Addiert, nicht gemaxt: Derselbe
        // Shard kann durchaus auf zwei Seiten liegen
        counts.clear();
        int felderGesamt = 0;
        for (Map<String, Integer> seite : perPage.values()) {
            for (Map.Entry<String, Integer> e : seite.entrySet()) {
                counts.merge(e.getKey(), e.getValue(), Integer::sum);
            }
            felderGesamt += seite.size();
        }
        pages = perPage.size();
        slotsSeen = felderGesamt;
        seenAt = now;
        lastTitle = clean(screen.getTitle().getString());
        lastSlots = slotsSeen;
        howCounted = ausText ? (ausStapel ? "lore and stack size" : "lore") : "stack size";
    }

    /**
     * Wie viele Stueck ein Feld darstellt.
     *
     * Ein Lager zeigt die Menge selten als Stapelgroesse - mehr als vierundsechzig
     * passen dort nicht hinein. Steht in der Beschreibung eine Zahl, gilt sie.
     */
    private static int amountOf(ItemStack stack) {
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore != null) {
            for (Component zeile : lore.lines()) {
                Matcher m = LORE_AMOUNT.matcher(clean(zeile.getString()));
                if (m.find()) {
                    int n = parse(m.group("n"));
                    if (n > 0) return n;
                }
            }
        }
        return stack.getCount();
    }

    private static int parse(String raw) {
        try {
            return Integer.parseInt(raw.replace(",", "").replace(".", "").trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Der Name in der Form, in der ihn die Fusionsdaten wiedererkennen.
     *
     * Im Spiel heisst das Feld "Abyssal Miner Shard", die Fusionsdaten fuehren
     * "Abyssal Miner". Wer beide roh vergleicht, findet nie etwas - sechsundvierzig
     * Sorten in der Box und null Treffer in der Liste sahen genau so aus. Beide
     * Seiten gehen deshalb durch dieselbe Funktion.
     */
    private static String key(String name) {
        return com.shokiteufel.shokimod.util.ShardProfitData.normalize(name);
    }

    private static String clean(String text) {
        return COLOUR_CODE.matcher(text == null ? "" : text).replaceAll("").trim();
    }

    /** Eine Zeile fuer den Diagnosebericht */
    public static String status() {
        if (seenAt == 0L) {
            return "shard stock: never looked into a shard window yet";
        }
        return "shard stock: " + counts.size() + " kinds from " + lastSlots + " slots over "
                + pages() + (totalPages > 0 ? "/" + totalPages : "") + " page(s) of \""
                + lastTitle + "\", counted by " + howCounted + ", seen " + age();
    }

    /**
     * Wie sich die Mengen verteilen.
     *
     * Die entscheidende Frage bei einem leeren Bestandsfilter: Werden die Stueckzahlen
     * ueberhaupt richtig gelesen? Stehen ueberall Einsen, stimmt etwas mit dem Ablesen
     * nicht - eine Fusion braucht meist fuenf je Zutat, und mit lauter Einsen kommt nie
     * eine zustande.
     */
    public static String amounts() {
        if (counts.isEmpty()) return "(nothing)";
        int eins = 0;
        int bisVier = 0;
        int abFuenf = 0;
        int groesste = 0;
        long summe = 0;
        for (int n : counts.values()) {
            if (n <= 1) eins++;
            else if (n < 5) bisVier++;
            else abFuenf++;
            groesste = Math.max(groesste, n);
            summe += n;
        }
        return "amounts: " + eins + "x one, " + bisVier + "x two-to-four, "
                + abFuenf + "x five or more, largest " + groesste
                + ", total " + summe;
    }

    /** Die ersten Eintraege im Klartext - damit sich pruefen laesst, was gelesen wurde */
    public static String sample(int howMany) {
        if (counts.isEmpty()) return "(nothing)";
        StringBuilder out = new StringBuilder();
        int n = 0;
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            if (n++ >= howMany) break;
            if (n > 1) out.append(", ");
            out.append(e.getKey()).append(" x").append(e.getValue());
        }
        return out.toString();
    }
}
