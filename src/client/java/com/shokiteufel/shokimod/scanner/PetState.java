package com.shokiteufel.shokimod.scanner;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import com.shokiteufel.shokimod.util.PetLevels;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Welches Pet gerade heraussen ist, und wie weit es auf dem Weg zur naechsten Stufe ist.
 *
 * Zwei Quellen, weil keine allein genuegt:
 *
 *   1. Die Autopet-Zeile im Chat. Sie kommt bei jedem Wechsel und nennt Stufe und Namen,
 *      aber nichts ueber den Fortschritt.
 *   2. Das Pet-Menue, sobald es offen ist. Dort steht in der Beschreibung des aktiven
 *      Pets, wie viel Erfahrung bis zur naechsten Stufe fehlt.
 *
 * Der Fortschritt bleibt also stehen, bis das Menue wieder geoeffnet wird - so wie bei
 * jeder Mod, die ihn nicht selbst mitzaehlen kann. Was zuletzt erkannt wurde, steht in
 * {@link #status()}: der Tester sieht das Spiel, nicht den Code.
 */
public final class PetState {

    /**
     * Der Wechsel des Pets in beiden Schreibweisen: "Autopet equipped your
     * [Lvl 100] Golden Dragon!" beim selbsttaetigen Wechsel, "You summoned your
     * [Lvl 100] Golden Dragon!" beim Ruf von Hand.
     *
     * Der Anfang der Zeile entscheidet: Meldungen ueber fremde Funde lauten
     * "[MVP+] Spieler has obtained [Lvl 1] EPIC Bal!" und duerfen nie greifen -
     * sonst zeigte der Kasten das Pet eines Fremden.
     */
    private static final Pattern AUTOPET = Pattern.compile(
            "^(?:Autopet equipped your|You summoned your) \\[Lvl (?<lvl>\\d+)\\] (?<name>.+?)!.*$", Pattern.CASE_INSENSITIVE);
    /** Dieselbe Angabe im Namen eines Pet-Feldes: "[Lvl 42] Ender Dragon" */
    private static final Pattern MENU_NAME = Pattern.compile("^\\[Lvl (?<lvl>\\d+)\\] (?<name>.+)$");
    /** "Progress to Level 41: 79.9%" */
    private static final Pattern PROGRESS = Pattern.compile(
            "^Progress to Level (?<next>\\d+): (?<percent>[\\d.,]+)%$", Pattern.CASE_INSENSITIVE);
    /** Die Zeile darunter: "18,532.1/23.2k" - erst gesammelt, dann noetig */
    private static final Pattern XP_LINE = Pattern.compile(
            "(?<have>[\\d.,]+[kKmMbB]?)\\s*/\\s*(?<need>[\\d.,]+[kKmMbB]?)\\s*$");
    /** Steht statt der Fortschrittszeile, sobald das Pet oben angekommen ist */
    private static final Pattern MAX_LEVEL = Pattern.compile("^MAX LEVEL$", Pattern.CASE_INSENSITIVE);
    /** Die Zeile darunter nennt die gesamte gesammelte Erfahrung: "> 18,617,563 XP" */
    private static final Pattern TOTAL_XP = Pattern.compile(
            "^[^0-9]{0,3}(?<xp>[0-9][0-9,.]*)\\s*XP$", Pattern.CASE_INSENSITIVE);
    /** Der Gegenstand, den das Pet traegt: "Held Item: Textbook" */
    private static final Pattern HELD_ITEM = Pattern.compile(
            "^Held Item:\\s*(?<item>.+)$", Pattern.CASE_INSENSITIVE);
    /**
     * Die Ueberschrift des Pet-Blocks in der Tab-Liste. Darunter stehen zwei Zeilen:
     * "[Lvl 200] [332*] Golden Dragon" und "+628,040,665.1 XP".
     *
     * Diese Quelle ist der Rechnung ueberlegen: Sie ist immer da, solange die
     * Tab-Liste steht, nennt den Ueberschuss fertig und sagt vor allem, welches Pet
     * WIRKLICH draussen ist - das Menue zeigt nur, was gerade angeklickt wurde.
     */
    private static final Pattern TAB_HEAD = Pattern.compile("^Pet:$", Pattern.CASE_INSENSITIVE);
    /** Die Zeile mit Stufe, moeglicher Ueberschuss-Stufe und Namen */
    private static final Pattern TAB_PET = Pattern.compile(
            "^\\[Lvl (?<lvl>\\d+)\\]\\s*(?:\\[(?<over>\\d+)[^\\]]*\\]\\s*)?(?<name>.+)$");
    /** Die Erfahrung darunter: "+628,040,665.1 XP" */
    private static final Pattern TAB_XP = Pattern.compile(
            "^\\+?(?<xp>[0-9][0-9,.]*)\\s*XP$", Pattern.CASE_INSENSITIVE);

    /** Was eine Stufe oberhalb der Hoechststufe kostet - fuer jedes Pet dasselbe */
    private static final int OVERFLOW_STEP = 1_886_700;
    /** Die Stufen, die Hypixel fuer Pets vergibt - in dieser Schreibweise */
    private static final java.util.Set<String> RARITIES = java.util.Set.of(
            "COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC", "DIVINE");
    /**
     * Was andere Mods dem Namen voranstellen, etwa "[332*]" fuer ihre eigene
     * Ueberschuss-Stufe. Ohne das Abstreifen hiesse das Pet spaeter so, die eigene
     * Rechnung liefe daneben, und im Kasten staende die Zahl doppelt.
     */
    private static final Pattern FOREIGN_TAG = Pattern.compile("^(?:\\[[^\\]]*\\]\\s*)+");
    /** Nur beim aktiven Pet steht das in der Beschreibung */
    private static final String ACTIVE_HINT = "click to despawn";
    private static final Pattern COLOUR_CODE = Pattern.compile("§.");
    /** Oefter als das lohnt sich der Blick ins offene Menue nicht */
    private static final long MENU_GAP_MILLIS = 500L;

    private static volatile String name = "";
    private static volatile int level = 0;
    private static volatile String rarity = "";
    private static volatile double percent = -1.0;
    private static volatile double xpHave = -1.0;
    private static volatile double xpNeed = -1.0;
    private static volatile double totalXp = -1.0;
    private static volatile boolean atMax = false;
    private static volatile int overflowLevel = 0;
    private static volatile double overflowXp = 0.0;
    private static volatile String heldItem = "";
    private static volatile net.minecraft.world.item.ItemStack icon =
            net.minecraft.world.item.ItemStack.EMPTY;
    private static volatile long seenAt = 0L;
    private static volatile String from = "nothing seen yet";
    private static long lastMenuLook = 0L;

    private PetState() {
    }

    public static boolean known() {
        return !name.isEmpty();
    }

    public static String name() {
        return name;
    }

    public static int level() {
        return level;
    }

    public static String rarity() {
        return rarity;
    }

    /** Fortschritt zur naechsten Stufe in Prozent, oder -1 wenn unbekannt */
    public static double percent() {
        return percent;
    }

    public static double xpHave() {
        return xpHave;
    }

    public static double xpNeed() {
        return xpNeed;
    }

    /** Gesamte gesammelte Erfahrung, oder -1 wenn unbekannt */
    public static double totalXp() {
        return totalXp;
    }

    /** Steht das Pet auf seiner Hoechststufe? */
    public static boolean atMaxLevel() {
        return atMax;
    }

    /** Stufen ueber der Hoechststufe - 0, wenn keine oder noch nicht ausgerechnet */
    public static int overflowLevel() {
        return overflowLevel;
    }

    /** Erfahrung oberhalb der Hoechststufe */
    public static double overflowXp() {
        return overflowXp;
    }

    /**
     * Stufe und Ueberschuss zusammengezaehlt: aus [Lvl 200] und 332 darueber wird 532.
     *
     * Gilt fuer jedes Pet gleich - die Drachen zaehlen ab ihrer Hoechststufe 200
     * weiter, alle anderen ab 100.
     */
    public static int combinedLevel() {
        return level + overflowLevel;
    }

    /** Der Gegenstand, den das Pet traegt, oder leer */
    public static String heldItem() {
        return heldItem;
    }

    /** Das Bild des Pets aus dem Menue, oder ein leerer Gegenstand */
    public static net.minecraft.world.item.ItemStack icon() {
        return icon;
    }

    /** Nach einem Weltwechsel gilt der alte Stand nicht mehr */
    public static void reset() {
        name = "";
        level = 0;
        rarity = "";
        percent = -1.0;
        xpHave = -1.0;
        xpNeed = -1.0;
        totalXp = -1.0;
        atMax = false;
        overflowLevel = 0;
        overflowXp = 0.0;
        heldItem = "";
        icon = net.minecraft.world.item.ItemStack.EMPTY;
        seenAt = 0L;
        from = "nothing seen yet";
    }

    /** Eine Zeile fuer den Diagnosebericht */
    public static String status() {
        if (!known()) return "active pet: none seen yet";
        StringBuilder out = new StringBuilder("active pet: ")
                .append('[').append(level).append("] ").append(name);
        if (!rarity.isEmpty()) out.append(" (").append(rarity.toLowerCase(Locale.ROOT)).append(')');
        if (percent >= 0) out.append(String.format(Locale.US, ", %.1f%% to next", percent));
        if (atMax) out.append(", MAX");
        if (overflowLevel > 0) out.append(", +").append(overflowLevel).append(" overflow");
        if (!heldItem.isEmpty()) out.append(", holding ").append(heldItem);
        if (!icon.isEmpty()) out.append(", icon ok");
        out.append(", ").append(com.shokiteufel.shokimod.util.PetIcons.size()).append(" icon(s) remembered");
        out.append(", from ").append(from);
        if (seenAt > 0) {
            long seconds = Math.max(0, (System.currentTimeMillis() - seenAt) / 1000L);
            out.append(", seen ").append(seconds < 60 ? seconds + "s ago" : (seconds / 60) + "min ago");
        }
        return out.toString();
    }

    /** Der Wechsel per Autopet - die einzige Meldung, die ohne offenes Menue kommt */
    public static void onChatMessage(String plain) {
        Matcher m = AUTOPET.matcher(plain.trim());
        if (!m.matches()) return;
        String neu = cleanName(m.group("name"));
        int stufe = parseInt(m.group("lvl"));
        if (neu.isEmpty() || stufe <= 0) return;
        // Ein anderes Pet heisst: der alte Fortschritt gehoert nicht mehr dazu
        if (!neu.equalsIgnoreCase(name)) {
            percent = -1.0;
            xpHave = -1.0;
            xpNeed = -1.0;
            rarity = "";
            totalXp = -1.0;
            atMax = false;
            heldItem = "";
            // Was ueber das neue Pet schon bekannt war, gilt sofort. Beim Angeln mit
            // Pet-Regeln wechselt es im Sekundentakt, und die Tab-Liste braucht laenger
            // als der Chat - ohne das stuende der Kasten nach jedem Wechsel kurz ohne
            // Ueberschuss da
            icon = com.shokiteufel.shokimod.util.PetIcons.iconFor(neu);
            overflowLevel = com.shokiteufel.shokimod.util.PetIcons.overflowLevelFor(neu);
            overflowXp = com.shokiteufel.shokimod.util.PetIcons.overflowXpFor(neu);
            atMax = overflowLevel > 0;
        }
        name = neu;
        level = stufe;
        seenAt = System.currentTimeMillis();
        from = "autopet";
    }

    /**
     * Das aktive Pet aus der Tab-Liste.
     *
     * Was hier steht, hat Vorrang vor dem Menue: Die Tab-Liste zeigt immer das Pet,
     * das gerade draussen ist, waehrend im Menue auch ein anderes angeklickt sein
     * kann. Genau daran lag es, dass zwischendurch das falsche Pet im Kasten stand.
     */
    public static void processTabList(java.util.List<String> lines) {
        for (int i = 0; i < lines.size() - 1; i++) {
            if (!TAB_HEAD.matcher(clean(lines.get(i))).matches()) continue;

            Matcher pet = TAB_PET.matcher(clean(lines.get(i + 1)));
            if (!pet.matches()) return;
            String neuerName = cleanName(pet.group("name"));
            int stufe = parseInt(pet.group("lvl"));
            if (neuerName.isEmpty() || stufe <= 0) return;

            // Ein anderes Pet heisst: alles Gemerkte gehoert nicht mehr dazu
            if (!neuerName.equalsIgnoreCase(name)) {
                percent = -1.0;
                xpHave = -1.0;
                xpNeed = -1.0;
                rarity = "";
                totalXp = -1.0;
                atMax = false;
                heldItem = "";
                icon = com.shokiteufel.shokimod.util.PetIcons.iconFor(neuerName);
                overflowLevel = com.shokiteufel.shokimod.util.PetIcons.overflowLevelFor(neuerName);
                overflowXp = com.shokiteufel.shokimod.util.PetIcons.overflowXpFor(neuerName);
            }
            name = neuerName;
            level = stufe;
            // Nach einem Neustart ist noch kein Bild da - das gemerkte springt ein
            if (icon.isEmpty()) icon = com.shokiteufel.shokimod.util.PetIcons.iconFor(neuerName);

            String ueber = pet.group("over");
            overflowLevel = ueber == null ? 0 : parseInt(ueber);
            atMax = overflowLevel > 0 || atMax;

            // Die Erfahrungszeile steht direkt darunter, kann aber fehlen
            if (i + 2 < lines.size()) {
                Matcher xp = TAB_XP.matcher(clean(lines.get(i + 2)));
                if (xp.matches()) overflowXp = parseAmount(xp.group("xp"));
            }
            // Fuer die drei Drachen nennt die Tab-Liste die Ueberschuss-Stufe selbst.
            // Fuer alle anderen steht dort nur die Erfahrung - die Stufe ergibt sich
            // daraus nach derselben Regel: je volle Kosten der letzten Stufe eine mehr.
            if (overflowLevel <= 0 && overflowXp > 0) {
                int schritt = overflowStep();
                if (schritt > 0) {
                    overflowLevel = (int) Math.floor(overflowXp / schritt);
                    atMax = atMax || overflowLevel > 0;
                }
            }
            if (overflowLevel > 0) {
                com.shokiteufel.shokimod.util.PetIcons.rememberOverflow(name, overflowLevel, overflowXp);
            } else {
                // Die Tab-Liste sagt gerade nichts dazu - dann gilt der letzte Stand,
                // statt eine Luecke zu zeigen, wo eben noch eine Zahl war
                int gemerkt = com.shokiteufel.shokimod.util.PetIcons.overflowLevelFor(name);
                if (gemerkt > 0) {
                    overflowLevel = gemerkt;
                    overflowXp = com.shokiteufel.shokimod.util.PetIcons.overflowXpFor(name);
                }
            }
            seenAt = System.currentTimeMillis();
            from = "tab list";
            return;
        }
    }

    /** Der Blick ins offene Pet-Menue: nur dort steht der Fortschritt */
    public static void tick(Minecraft client) {
        if (client == null || !(client.screen instanceof AbstractContainerScreen<?> screen)) return;
        long now = System.currentTimeMillis();
        if (now - lastMenuLook < MENU_GAP_MILLIS) return;
        lastMenuLook = now;

        // Das Pet-Menue heisst "(1/5) Pets" - die Seitenzahl steht VOR dem Namen, ein
        // Anfangsvergleich geht deshalb ins Leere und schloss ab 1.3.13 das ganze Menue
        // aus. Der Plural genuegt zur Abgrenzung: der Pet-Sitter heisst "Pet Sitter"
        // und traegt kein s, taucht hier also nicht auf
        String title = clean(screen.getTitle().getString()).toLowerCase(Locale.ROOT);
        if (!title.contains("pets")) return;

        boolean aktivesGefunden = false;
        for (Slot slot : screen.getMenu().slots) {
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;
            Matcher named = MENU_NAME.matcher(clean(stack.getHoverName().getString()));
            if (!named.matches()) continue;

            // Jedes Pet im Menue merken, nicht nur das aktive: Wer spaeter ein anderes
            // ausruestet, haette sonst wieder kein Bild und muesste das Menue erneut
            // oeffnen - genau das soll das Merken ja ersparen
            com.shokiteufel.shokimod.util.PetIcons.remember(cleanName(named.group("name")), stack);

            ItemLore lore = stack.get(DataComponents.LORE);
            if (lore == null) continue;
            if (!aktivesGefunden && readActive(named, lore, stack)) aktivesGefunden = true;
        }
    }

    /** Liest ein Feld aus, wenn es das aktive Pet ist. Wahr, sobald es passt */
    private static boolean readActive(Matcher named, ItemLore lore, ItemStack stack) {
        boolean active = false;
        String seltenheit = "";
        double prozent = -1.0;
        double haben = -1.0;
        double noetig = -1.0;
        String vorige = "";
        boolean maxErreicht = false;
        boolean naechsteIstGesamt = false;
        double gesamt = -1.0;
        String getragen = "";

        for (Component zeile : lore.lines()) {
            String text = clean(zeile.getString()).trim();
            String klein = text.toLowerCase(Locale.ROOT);
            if (klein.contains(ACTIVE_HINT)) active = true;
            // Die Seltenheit steht als eine der letzten Zeilen. Gesucht wird sie als
            // ganzes Wort, nicht als ganze Zeile: andere Mods haengen dort gern etwas
            // an ("LEGENDARY PET"), und eine Zeile, die nur auf Grossbuchstaben prueft,
            // schluckte auch "MAX LEVEL" - dann rechnete der Ueberschuss mit dem
            // Versatz von COMMON und laege weit daneben
            for (String stufe : RARITIES) {
                if (text.equals(stufe) || text.startsWith(stufe + " ") || text.endsWith(" " + stufe)) {
                    seltenheit = stufe;
                    break;
                }
            }

            if (MAX_LEVEL.matcher(text).matches()) {
                maxErreicht = true;
                naechsteIstGesamt = true;
                continue;
            }
            if (naechsteIstGesamt) {
                naechsteIstGesamt = false;
                Matcher t = TOTAL_XP.matcher(text);
                if (t.matches()) gesamt = parseAmount(t.group("xp"));
            }
            Matcher h = HELD_ITEM.matcher(text);
            if (h.matches()) {
                getragen = h.group("item").trim();
                continue;
            }

            Matcher p = PROGRESS.matcher(text);
            if (p.matches()) {
                prozent = parseDouble(p.group("percent"));
                vorige = text;
                continue;
            }
            // Die Zahlenzeile steht direkt unter der Prozentzeile
            if (!vorige.isEmpty()) {
                Matcher xp = XP_LINE.matcher(text);
                if (xp.find()) {
                    haben = parseAmount(xp.group("have"));
                    noetig = parseAmount(xp.group("need"));
                }
                vorige = "";
            }
        }
        if (!active) return false;

        name = cleanName(named.group("name"));
        level = parseInt(named.group("lvl"));
        if (!seltenheit.isEmpty()) rarity = seltenheit;
        percent = prozent;
        xpHave = haben;
        xpNeed = noetig;
        atMax = maxErreicht || atMax;
        totalXp = gesamt;
        heldItem = getragen;
        icon = stack.copy();
        // Damit der Kasten sein Bild auch nach einem Neustart hat, ohne dass jemand
        // erst wieder das Pet-Menue oeffnen muss
        com.shokiteufel.shokimod.util.PetIcons.remember(name, icon);
        // Nur selbst rechnen, wenn die Tab-Liste nichts geliefert hat - sie ist genauer
        if (overflowLevel <= 0) computeOverflow();
        seenAt = System.currentTimeMillis();
        from = "pet menu";
        return true;
    }

    /**
     * Was eine Ueberschuss-Stufe kostet: immer 1.886.700 Erfahrung.
     *
     * Das ist die Vorgabe von ShokiTeufel (10.09.2026) und gilt fuer jedes Pet gleich,
     * unabhaengig von der Seltenheit - so wie es auch bei den drei Drachen gerechnet
     * wird. Die Seltenheit spielt nur bis zur Hoechststufe eine Rolle; was darueber
     * gesammelt wird, zaehlt fuer alle nach demselben Mass.
     */
    private static int overflowStep() {
        return OVERFLOW_STEP;
    }

    /**
     * Stufen und Erfahrung oberhalb der Hoechststufe.
     *
     * Hypixel zeigt sie nicht; sie sind eine Rechnung der Mods: Wer oben angekommen ist,
     * sammelt weiter, und je volle Kosten der letzten Stufe zaehlt eine Stufe darueber.
     * Nachgerechnet an einem Golden Dragon mit 627.958.704,5 Erfahrung im Ueberschuss -
     * geteilt durch 1.886.700 ergibt 332,8, und im Spiel stand [332].
     */
    private static void computeOverflow() {
        overflowLevel = 0;
        overflowXp = 0.0;
        if (!atMax || totalXp < 0) return;
        // Ohne Seltenheit stimmt der Versatz nicht, und die Zahl waere frei erfunden
        if (rarity.isEmpty()) return;

        PetLevels.Table table = PetLevels.tableFor(PetLevels.idFor(name), rarity);
        if (table == null) return;   // die Tabellen sind noch nicht geladen
        double bisOben = table.totalTo(table.maxLevel());
        // Ueber der Hoechststufe zaehlt fuer jedes Pet dasselbe Mass
        int schritt = OVERFLOW_STEP;

        double ueber = totalXp - bisOben;
        if (ueber <= 0) return;
        overflowXp = ueber;
        overflowLevel = (int) Math.floor(ueber / schritt);
        if (overflowLevel > 0) {
            com.shokiteufel.shokimod.util.PetIcons.rememberOverflow(name, overflowLevel, overflowXp);
        }
    }

    private static String clean(String text) {
        return COLOUR_CODE.matcher(text == null ? "" : text).replaceAll("").trim();
    }

    /** Der blosse Name, ohne was andere Mods davorgesetzt haben */
    private static String cleanName(String text) {
        return FOREIGN_TAG.matcher(clean(text)).replaceFirst("").trim();
    }

    private static int parseInt(String raw) {
        try {
            return Integer.parseInt(raw.replace(",", "").replace(".", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static double parseDouble(String raw) {
        try {
            return Double.parseDouble(raw.replace(",", ""));
        } catch (NumberFormatException e) {
            return -1.0;
        }
    }

    /** "23.2k" sind 23200, "1.4M" sind 1400000 */
    private static double parseAmount(String raw) {
        if (raw == null || raw.isEmpty()) return -1.0;
        String text = raw.replace(",", "").trim();
        double faktor = switch (Character.toLowerCase(text.charAt(text.length() - 1))) {
            case 'k' -> 1_000d;
            case 'm' -> 1_000_000d;
            case 'b' -> 1_000_000_000d;
            default -> 1d;
        };
        if (faktor > 1d) text = text.substring(0, text.length() - 1);
        try {
            return Double.parseDouble(text) * faktor;
        } catch (NumberFormatException e) {
            return -1.0;
        }
    }
}
