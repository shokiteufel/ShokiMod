package com.shokiteufel.shokimod.scanner;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import com.shokiteufel.shokimod.util.PetIcons;
import com.shokiteufel.shokimod.util.PetIdentity;
import com.shokiteufel.shokimod.util.PetLevels;
import com.shokiteufel.shokimod.util.PetLog;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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
     * Golden Dragon!" beim Ruf von Hand. Die zweite Zeile nennt keine Stufe - SkyHanni
     * kennt sie so (Test: "You summoned your Rabbit ✦!"); das Muster verlangte sie bis
     * 1.19.4 und las damit den Ruf von Hand nie.
     *
     * Der Anfang der Zeile entscheidet: Meldungen ueber fremde Funde lauten
     * "[MVP+] Spieler has obtained [Lvl 1] EPIC Bal!" und duerfen nie greifen -
     * sonst zeigte der Kasten das Pet eines Fremden.
     */
    private static final Pattern AUTOPET = Pattern.compile(
            "^(?:Autopet equipped your|You summoned your) (?:\\[Lvl (?<lvl>\\d+)\\] )?(?<name>.+?)!.*$", Pattern.CASE_INSENSITIVE);
    /** Dieselbe Angabe im Namen eines Pet-Feldes: "[Lvl 42] Ender Dragon" */
    /**
     * Der Name eines Feldes im Pet-Menue: "[Lvl 87] Enderman".
     *
     * Zwischen Zahl und Klammer darf noch etwas stehen. Bei Pets auf Hoechststufe
     * setzen Hypixel und andere Mods dort ein Zeichen ("[Lvl 100\u2726]"), und ein
     * Muster, das nur Ziffern duldet, uebersprang das Feld stillschweigend. Getroffen
     * hat das genau ein Pet - das getragene mit Ueberschuss - waehrend alle anderen
     * sauber gemerkt wurden: Deshalb standen dort siebenundachtzig Bilder und
     * ausgerechnet das gesuchte fehlte. Die Tab-Liste war laengst so tolerant;
     * das Menue blieb streng, obwohl es dieselben Namen liest.
     */
    private static final Pattern MENU_NAME = Pattern.compile(
            "^\\[Lvl (?<lvl>\\d+)[^\\]]*\\]\\s*(?<name>.+)$");
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
    /**
     * Die Zeile mit Stufe, moeglicher Ueberschuss-Stufe und Namen.
     *
     * Hinter der Zahl darf noch etwas stehen: Bei einem Pet auf Hoechststufe setzt
     * Hypixel dort ein Zeichen, "[Lvl 100✦]". Das Muster im Menue war dafuer
     * laengst tolerant, dieses hier nicht - und weil ein Treffer Pflicht ist, kam aus
     * der Tab-Liste gar nichts mehr an. Die Anzeige blieb auf dem Stand des letzten
     * Menue-Besuchs stehen, waehrend im Chat weiter Stufen gemeldet wurden.
     */
    private static final Pattern TAB_PET = Pattern.compile(
            "^\\[Lvl (?<lvl>\\d+)[^\\]]*\\]\\s*(?:\\[(?<over>\\d+)[^\\]]*\\]\\s*)?(?<name>.+)$");
    /** Die Erfahrung darunter: "+628,040,665.1 XP" */
    private static final Pattern TAB_XP = Pattern.compile(
            "^\\+?(?<xp>[0-9][0-9,.]*)\\s*XP$", Pattern.CASE_INSENSITIVE);
    /**
     * Der Fortschritt eines Pets unter der Hoechststufe: "33,915/179.7k XP (18.9%)". Diese Zeile und "MAX LEVEL"
     * standen bis 1.19.4 fuer den Namen des Pet-Items - bei einem Pet ohne Item steht sie dort, wo sonst das Item
     * steht. Solange das Item nicht angezeigt wurde, fiel es nicht auf.
     */
    private static final Pattern TAB_PROGRESS = Pattern.compile(
            "^[0-9][0-9,.]*[kKmMbB]?\\s*/\\s*[0-9][0-9,.]*[kKmMbB]?\\s*XP(?:\\s*\\([0-9.]+%\\))?$", Pattern.CASE_INSENSITIVE);
    /** Der Mauszeiger-Text der Autopet-Zeile nennt das Item: "Held Item: Poignant Lucky Clover" */
    private static final Pattern HOVER_ITEM = Pattern.compile(
            "Held Item:\\s*(?<item>.+?)\\s*$", Pattern.CASE_INSENSITIVE);

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
    /**
     * Derselbe Name, wie Hypixel ihn schreibt - mit dem Zeichen eines Pet-Skins.
     *
     * Getrennt gehalten, weil beide etwas anderes sollen: Zugeordnet wird ueber den
     * blossen Namen, sonst findet ein Pet mit Skin sein Bild nicht wieder. Gezeigt
     * wird, was im Spiel steht - sonst sieht man dem Kasten nicht mehr an, dass das
     * Pet einen Skin traegt.
     */
    private static volatile String shown = "";
    private static volatile int level = 0;
    /**
     * Woran das Pet erkannt wird: Name, Skin-Zeichen und Item (siehe PetIdentity). Zwei Pets gleichen Namens haben
     * zwei Schluessel - Bild und Ueberschuss gehoeren zum Schluessel, nicht zum Namen.
     */
    private static volatile String key = "";
    /** Traegt das Pet einen Skin? Das Zeichen hinter dem Namen sagt es in jeder Quelle */
    private static volatile boolean skin = false;
    /** Wann die Autopet-Zeile oder das Pet-Menue zuletzt gesagt haben, welches Pet draussen ist - und welches */
    private static volatile long assertedAt = 0L;
    private static volatile String assertedKey = "";
    /**
     * So lange gilt die Autopet-Zeile oder das Menue mehr als die Tab-Liste. Die Tab-Liste braucht nach einem Wechsel
     * einen Augenblick und zeigt in der Zwischenzeit noch das alte Pet; ohne die Frist sprang der Kasten zurueck und
     * zeigte die Erfahrung des einen beim anderen. SkyHanni wartet ebenso fuenf Sekunden.
     */
    private static final long TAB_DELAY_MILLIS = 5000L;
    /** Fuer das Protokoll: was zuletzt aus der Tab-Liste gelesen und entschieden wurde */
    private static String lastTabSignature = "";
    private static long lastTabXpLog = 0L;
    private static boolean menuOpen = false;
    private static final Set<String> menuLogged = new HashSet<>();
    private static volatile String rarity = "";
    /** Die Tab-Liste kennt die Seltenheit schon - dann zaehlt das Menue nicht dagegen */
    private static volatile boolean rarityFromTab = false;
    private static volatile double percent = -1.0;
    private static volatile double xpHave = -1.0;
    private static volatile double xpNeed = -1.0;
    private static volatile double totalXp = -1.0;
    /**
     * Zu welchem Pet die gesamte Erfahrung gehoert.
     *
     * Die Zahl allein sagt das nicht, und wird sie mit der Stufentabelle eines anderen
     * verrechnet, kommt eine Stufe heraus, die es nie gab: Die 878.141.630 Erfahrung
     * eines Golden Dragon ergeben mit der Tabelle des Ammonite genau 452 - und so oft
     * stand "leveled up to level 452" im Chat.
     */
    private static volatile String totalXpPet = "";
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

    /** Fuer die Anzeige: mit dem Skin-Zeichen, falls eines dranhaengt */
    public static String shownName() {
        return shown.isEmpty() ? name : shown;
    }

    public static String name() {
        return name;
    }

    /** Der Schluessel des aktiven Pets (Name, Skin, Item) - fuer Bild und Ueberschuss */
    public static String key() {
        return key.isEmpty() ? name : key;
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
        shown = "";
        key = "";
        skin = false;
        assertedAt = 0L;
        assertedKey = "";
        lastTabSignature = "";
        menuOpen = false;
        menuLogged.clear();
        level = 0;
        rarity = "";
        percent = -1.0;
        xpHave = -1.0;
        xpNeed = -1.0;
        totalXp = -1.0;
        totalXpPet = "";
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
        out.append(" {key ").append(key()).append('}');
        if (!rarity.isEmpty()) out.append(" (").append(rarity.toLowerCase(Locale.ROOT)).append(')');
        if (percent >= 0) out.append(String.format(Locale.US, ", %.1f%% to next", percent));
        if (atMax) out.append(", MAX");
        // Auch die Erfahrung nennen, nicht nur die Stufe: Ohne sie laesst sich "keine
        // Ueberschuss-Zeile im Tab" nicht von "Erfahrung reicht noch nicht" trennen -
        // und genau diese Frage stellt sich, wenn im Kasten nichts steht
        if (overflowXp > 0) {
            out.append(String.format(Locale.US, ", overflow %.0f xp = %d level(s) [%d per level]",
                    overflowXp, overflowLevel, OVERFLOW_STEP));
        } else {
            out.append(", no overflow xp in tab");
        }
        if (!heldItem.isEmpty()) out.append(", holding ").append(heldItem);
        out.append(icon.isEmpty() ? ", NO ICON" : ", icon ok");
        out.append(", ").append(com.shokiteufel.shokimod.util.PetIcons.withImage())
           .append(" image(s) of ").append(com.shokiteufel.shokimod.util.PetIcons.size())
           .append(" entries remembered");
        // Fehlt das Bild, sagt die blosse Anzahl nichts darueber, woran es liegt:
        // ob der Eintrag fehlt, ob er nur die Ueberschuss-Stufe traegt, oder ob er
        // da ist und sich bloss nicht bauen laesst. Das steht hier
        if (icon.isEmpty() && !name.isEmpty()) {
            out.append(", stored: ")
               .append(com.shokiteufel.shokimod.util.PetIcons.describe(key()));
        }
        out.append(", from ").append(from);
        if (seenAt > 0) {
            long seconds = Math.max(0, (System.currentTimeMillis() - seenAt) / 1000L);
            out.append(", seen ").append(seconds < 60 ? seconds + "s ago" : (seconds / 60) + "min ago");
        }
        return out.toString();
    }

    /**
     * Die Chatzeilen zum Pet. Der Wechsel per Autopet ist die einzige Meldung ueber das aktive Pet, die ohne offenes
     * Menue kommt.
     *
     * Die Zeile nennt Stufe, Namen und das Skin-Zeichen; das Item steht nur im Mauszeiger-Text hinter "VIEW RULE" (so
     * liest es auch SkyHanni). Damit ist bei zwei gleichnamigen Pets sofort das richtige gemeint - die Tab-Liste
     * braucht dafuer eine Weile und zeigt in der Zwischenzeit noch das alte (siehe processTabList).
     */
    public static void onChatMessage(Component message, String plain) {
        String line = plain.trim();
        if (isPetLine(line)) logChatLine(message, line);
        Matcher m = AUTOPET.matcher(line);
        if (!m.matches()) return;
        String neu = cleanName(m.group("name"));
        String neuGezeigt = displayName(m.group("name"));
        boolean neuSkin = hasSkinMark(neuGezeigt);
        int stufe = m.group("lvl") == null ? 0 : parseInt(m.group("lvl"));
        if (neu.isEmpty()) return;

        // Nur die Autopet-Zeile hat einen Mauszeiger-Text; "You summoned your" sagt zum Item nichts
        String hoverItem = line.regionMatches(true, 0, "Autopet", 0, 7) ? heldItemFromHover(message, neu) : null;
        PetIdentity.Resolution res = PetIdentity.resolve(PetIcons.knownKeys(), neu, neuSkin, hoverItem);
        boolean wechsel = !res.key().equalsIgnoreCase(key);
        // Ein anderes Pet heisst: der alte Fortschritt gehoert nicht mehr dazu. Was ueber das neue Pet schon bekannt
        // war, gilt sofort - beim Angeln mit Pet-Regeln wechselt es im Sekundentakt, und die Tab-Liste braucht laenger
        // als der Chat
        if (wechsel) switchTo(res.key(), true);
        name = neu;
        shown = neuGezeigt;
        skin = neuSkin;
        // Der Ruf von Hand nennt keine Stufe: die der Tab-Liste gilt weiter, solange es dasselbe Pet ist
        if (stufe > 0) level = stufe;
        else if (wechsel) level = 0;
        long now = System.currentTimeMillis();
        // Nur eine sichere Zuordnung haelt die Tab-Liste auf Abstand: ein Mauszeiger-Text mit Item, oder genau ein
        // gemerktes Pet dieser Art
        boolean sicher = !res.ambiguous() && (hoverItem != null || res.known());
        assertedAt = sicher ? now : 0L;
        assertedKey = sicher ? res.key() : "";
        seenAt = now;
        from = "autopet";
        PetLog.line("ID", "autopet says " + neu + (neuSkin ? " (skin)" : "") + (stufe > 0 ? " [Lvl " + stufe + "]" : " (no level given)") + ", hover item "
                + (hoverItem == null ? "not given" : hoverItem.isEmpty() ? "none" : "'" + hoverItem + "'")
                + " -> key " + res.key() + (res.known() ? "" : " (new)") + (res.ambiguous() ? " (guessed)" : "")
                + (wechsel ? ", pet changed" : ", same pet") + ", tab ignored for " + (sicher ? TAB_DELAY_MILLIS + " ms" : "0 ms"));
    }

    /** Zeilen, die das Protokoll sehen soll: alles, was ein Pet wechselt oder ein Item aendert */
    private static boolean isPetLine(String line) {
        return line.startsWith("Autopet") || line.startsWith("You summoned your")
                || line.startsWith("Your pet is now holding")
                || (line.startsWith("You removed ") && line.endsWith(" from your pet!"));
    }

    private static void logChatLine(Component message, String line) {
        StringBuilder out = new StringBuilder("'").append(line).append('\'');
        List<String> hover = hoverLines(message);
        if (!hover.isEmpty()) out.append(" hover=").append(hover);
        String click = clickOf(message);
        if (!click.isEmpty()) out.append(" click=").append(click);
        PetLog.line("CHAT", out.toString());
    }

    /**
     * Das Item aus dem Mauszeiger-Text der Autopet-Zeile.
     *
     * @return null, wenn die Zeile keinen Text mitbringt, der das Pet betrifft - dann weiss die Quelle nichts zum
     *         Item; leer, wenn der Text das Pet nennt, aber kein Item; sonst der Name des Items
     */
    private static String heldItemFromHover(Component message, String petName) {
        List<String> lines = hoverLines(message);
        if (lines.isEmpty()) return null;
        boolean umsPet = false;
        String item = "";
        String petLower = petName.toLowerCase(Locale.ROOT);
        for (String text : lines) {
            if (text.toLowerCase(Locale.ROOT).contains(petLower)) umsPet = true;
            Matcher h = HOVER_ITEM.matcher(text);
            if (h.find()) item = h.group("item").trim();
        }
        if (!item.isEmpty()) return item;
        return umsPet ? "" : null;
    }

    /** Alle Mauszeiger-Texte einer Zeile, ohne Farbcodes, jede Zeile einmal - der Stil vererbt sich auf die Teile */
    private static List<String> hoverLines(Component message) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        collectHover(message, out);
        return new ArrayList<>(out);
    }

    private static void collectHover(Component component, Set<String> out) {
        if (component.getStyle().getHoverEvent() instanceof HoverEvent.ShowText showText) {
            for (String part : showText.value().getString().split("\n")) {
                String text = clean(part);
                if (!text.isBlank()) out.add(text);
            }
        }
        for (Component sibling : component.getSiblings()) collectHover(sibling, out);
    }

    /** Das Klick-Ziel der Zeile ("VIEW RULE"), so wie das Spiel es nennt - fuer das Protokoll */
    private static String clickOf(Component component) {
        if (component.getStyle().getClickEvent() != null) return String.valueOf(component.getStyle().getClickEvent());
        for (Component sibling : component.getSiblings()) {
            String found = clickOf(sibling);
            if (!found.isEmpty()) return found;
        }
        return "";
    }

    /**
     * Auf ein anderes Pet umstellen. Alles Gemerkte des alten gehoert nicht mehr dazu; das Item kommt aus dem
     * Schluessel, damit die Zeile unter dem Namen gleich stimmt.
     *
     * @param fromMemory auch die Ueberschuss-Stufe gleich vom gemerkten Stand holen (Chat) - die Tab-Liste nennt sie
     *                   selbst und braucht das nicht
     */
    private static void switchTo(String newKey, boolean fromMemory) {
        key = newKey;
        percent = -1.0;
        xpHave = -1.0;
        xpNeed = -1.0;
        rarity = "";
        totalXp = -1.0;
        totalXpPet = "";
        atMax = false;
        heldItem = PetIdentity.itemOf(newKey);
        icon = PetIcons.iconFor(newKey);
        overflowLevel = PetIcons.overflowLevelFor(newKey);
        overflowXp = PetIcons.overflowXpFor(newKey);
        if (fromMemory) atMax = overflowLevel > 0;
    }

    /** Steht ein Zeichen hinter dem Namen ("Ammonite ✦")? Hypixel setzt es bei einem Pet mit Skin */
    private static boolean hasSkinMark(String displayName) {
        return SKIN_MARK.matcher(displayName).find();
    }

    /**
     * Das aktive Pet aus der Tab-Liste.
     *
     * Was hier steht, hat Vorrang vor dem Menue: Die Tab-Liste zeigt immer das Pet,
     * das gerade draussen ist, waehrend im Menue auch ein anderes angeklickt sein
     * kann. Genau daran lag es, dass zwischendurch das falsche Pet im Kasten stand.
     *
     * Eine Ausnahme: Gleich nach einer Autopet-Zeile oder dem Menue zeigt sie noch das alte Pet. Fuenf Sekunden lang
     * gilt dann, was Chat und Menue gesagt haben - sonst sprang der Kasten zurueck und wieder vor, und die Erfahrung
     * des einen stand beim anderen.
     */
    public static void processTabList(java.util.List<String> lines, java.util.List<String> formatted) {
        for (int i = 0; i < lines.size() - 1; i++) {
            if (!TAB_HEAD.matcher(clean(lines.get(i))).matches()) continue;

            Matcher pet = TAB_PET.matcher(clean(lines.get(i + 1)));
            if (!pet.matches()) return;
            String neuerName = cleanName(pet.group("name"));
            String gezeigt = displayName(pet.group("name"));
            boolean neuSkin = hasSkinMark(gezeigt);
            int stufe = parseInt(pet.group("lvl"));
            if (neuerName.isEmpty() || stufe <= 0) return;

            // Die Erfahrungszeile steht darunter - aber nicht immer unmittelbar.
            //
            // Haelt das Pet ein Item, schiebt sich dessen Name dazwischen:
            //
            //     Pet:
            //     [Lvl 100] Black Cat
            //     Lucky Clover          <- nur bei Pets mit Item
            //     +31,545,449.2 XP
            //
            // Nachgesehen wurde bisher allein in der Zeile direkt darunter. Bei
            // einem Pet ohne Item stand die Erfahrung genau dort und alles stimmte;
            // mit Item stand dort das Item, und die Ueberschuss-Stufe fehlte immer.
            // Gesucht wird deshalb ueber die naechsten Zeilen, bis die Zahl kommt
            // oder der Abschnitt endet ("Pet Training:" faengt den naechsten an).
            //
            // Unter der Hoechststufe steht dort statt der Zahl der Fortschritt ("33,915/179.7k XP (18.9%)") und auf
            // der Hoechststufe "MAX LEVEL". Beides ist kein Item - bis 1.19.4 wurde es dafuer gehalten
            double gefundeneXp = -1.0;
            String gefundenesItem = "";
            boolean abgeschlossen = false;
            for (int j = i + 2; j <= i + 4; j++) {
                if (j >= lines.size()) {
                    abgeschlossen = true;
                    break;
                }
                String zeile = clean(lines.get(j));
                if (zeile.isEmpty() || zeile.endsWith(":")) {
                    abgeschlossen = true;
                    break;
                }
                Matcher xp = TAB_XP.matcher(zeile);
                if (xp.matches()) {
                    gefundeneXp = parseAmount(xp.group("xp"));
                    abgeschlossen = true;
                    break;
                }
                if (TAB_PROGRESS.matcher(zeile).matches() || MAX_LEVEL.matcher(zeile).matches()) {
                    abgeschlossen = true;
                    break;
                }
                // Weder Zahl noch Ueberschrift: das ist der Name des Items
                if (gefundenesItem.isEmpty()) gefundenesItem = zeile;
            }
            // null heisst: die Liste war an dieser Stelle noch nicht fertig aufgebaut - dann weiss sie zum Item nichts
            String itemGesehen = !gefundenesItem.isEmpty() ? gefundenesItem : (abgeschlossen ? "" : null);

            PetIdentity.Resolution res = PetIdentity.resolve(PetIcons.knownKeys(), neuerName, neuSkin, itemGesehen);
            long now = System.currentTimeMillis();
            boolean anderes = !res.key().equalsIgnoreCase(key);
            boolean wartet = anderes && assertedAt > 0 && now - assertedAt < TAB_DELAY_MILLIS
                    && !res.key().equalsIgnoreCase(assertedKey);
            logTab(lines, i, neuerName, neuSkin, itemGesehen, stufe, res, wartet);
            if (wartet) return;

            // Ein anderes Pet heisst: alles Gemerkte gehoert nicht mehr dazu
            if (anderes) switchTo(res.key(), false);
            name = neuerName;
            shown = gezeigt;
            skin = neuSkin;
            level = stufe;
            // Die Farbe des Namens in der Tab-Liste ist die Seltenheit - das gilt auch ohne
            // je das Pet-Menue geoeffnet zu haben und stimmt immer fuer das Pet, das draussen ist
            String tabRarity = rarityFromColour(formatted.get(i + 1), gezeigt);
            if (!tabRarity.isEmpty()) rarity = tabRarity;
            rarityFromTab = !tabRarity.isEmpty();
            // Nach einem Neustart ist noch kein Bild da - das gemerkte springt ein
            if (icon.isEmpty()) icon = PetIcons.iconFor(key);

            String ueber = pet.group("over");
            overflowLevel = ueber == null ? 0 : parseInt(ueber);
            atMax = overflowLevel > 0 || atMax;

            // Nur wenn die Zahl in DIESEM Durchlauf dastand, gehoert sie zu diesem Pet.
            // Fehlt sie - beim Wechsel baut die Tab-Liste einen Augenblick lang um -,
            // bleibt der alte Wert stehen, und der ist dann der des anderen Pets
            boolean frischeXp = gefundeneXp > 0;
            if (frischeXp) overflowXp = gefundeneXp;
            // Das Item nur setzen, nicht loeschen - beim Pet-Wechsel raeumt switchTo ohnehin auf, und das Menue
            // weiss es manchmal besser
            if (!gefundenesItem.isEmpty()) heldItem = gefundenesItem;
            // Fuer die drei Drachen nennt die Tab-Liste die Ueberschuss-Stufe selbst.
            // Fuer alle anderen steht dort nur die Erfahrung - die Stufe ergibt sich
            // daraus nach derselben Regel: je volle Kosten der letzten Stufe eine mehr.
            // Gerechnet wird nur mit Erfahrung, die eben dastand. Ein stehengebliebener
            // Wert gehoert womoeglich zum vorigen Pet, und aus dem wuerde hier eine
            // Stufe, die es nie gab
            if (overflowLevel <= 0 && overflowXp > 0 && frischeXp) {
                int schritt = overflowStep();
                if (schritt > 0) {
                    overflowLevel = (int) Math.floor(overflowXp / schritt);
                    atMax = atMax || overflowLevel > 0;
                }
            }
            // Gemerkt - und damit auch gemeldet - wird nur, was belegt ist: entweder
            // die Tab-Liste nennt die Stufe selbst, oder die Erfahrung dazu stand
            // eben in dieser Zeile.
            //
            // Ohne diese Bedingung wanderte beim Pet-Wechsel die Erfahrung des einen
            // unter den Namen des anderen. Wer im Sekundentakt zwischen Golden Dragon
            // und Ammonite wechselt, bekam dadurch endlos "leveled up to level 452"
            // gemeldet: Stufe 100 vom Ammonite, 664 Millionen Erfahrung vom Drachen.
            // Die gemerkte Zahl sprang zwischen 309 und 352 hin und her, und jeder
            // Sprung nach oben galt als Aufstieg
            if (overflowLevel > 0 && (frischeXp || ueber != null)) {
                PetIcons.rememberOverflow(key, name, overflowLevel, overflowXp, combinedLevel());
            } else {
                // Die Tab-Liste sagt gerade nichts Belegtes dazu - dann gilt der letzte
                // Stand DIESES Pets, statt eine Luecke zu zeigen oder eine Zahl, die vom
                // vorigen stammt
                int gemerkt = PetIcons.overflowLevelFor(key);
                overflowLevel = Math.max(0, gemerkt);
                overflowXp = gemerkt > 0 ? PetIcons.overflowXpFor(key) : 0.0;
            }
            seenAt = System.currentTimeMillis();
            from = "tab list";
            return;
        }
    }

    /**
     * Was die Tab-Liste gesagt und die Mod daraus gemacht hat - ins Protokoll, aber nur bei einer Aenderung: Die
     * Erfahrung wandert mit jedem Kill und gehoert nicht in jede Zeile (ein Stand je Minute reicht).
     */
    private static void logTab(java.util.List<String> lines, int at, String petName, boolean petSkin, String item,
                               int stufe, PetIdentity.Resolution res, boolean wartet) {
        String signature = petName + "|" + petSkin + "|" + item + "|" + stufe + "|" + res.key() + "|" + wartet;
        long now = System.currentTimeMillis();
        boolean changed = !signature.equals(lastTabSignature);
        if (!changed && now - lastTabXpLog < 60_000L) return;
        lastTabSignature = signature;
        lastTabXpLog = now;
        StringBuilder block = new StringBuilder();
        for (int j = at; j < lines.size() && j <= at + 4; j++) {
            if (j > at) block.append(" | ");
            block.append(clean(lines.get(j)));
        }
        PetLog.line("TAB", "[" + block + "] -> " + petName + (petSkin ? " (skin)" : "") + ", item "
                + (item == null ? "unknown (list not complete)" : item.isEmpty() ? "none" : "'" + item + "'")
                + ", key " + res.key() + (res.known() ? "" : " (new)") + (res.ambiguous() ? " (guessed)" : "")
                + (wartet ? " - NOT taken over, autopet/menu said " + assertedKey + " " + (now - assertedAt) + " ms ago"
                        : (changed ? " - taken over" : " - xp sample")));
    }

    /** Der Blick ins offene Pet-Menue: nur dort steht der Fortschritt */
    public static void tick(Minecraft client) {
        if (client == null || !(client.gui.screen() instanceof AbstractContainerScreen<?> screen)) {
            menuOpen = false;
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastMenuLook < MENU_GAP_MILLIS) return;
        lastMenuLook = now;

        // Das Pet-Menue heisst "(1/5) Pets" - die Seitenzahl steht VOR dem Namen, ein
        // Anfangsvergleich geht deshalb ins Leere und schloss ab 1.3.13 das ganze Menue
        // aus. Der Plural genuegt zur Abgrenzung: der Pet-Sitter heisst "Pet Sitter"
        // und traegt kein s, taucht hier also nicht auf
        String title = clean(screen.getTitle().getString()).toLowerCase(Locale.ROOT);
        if (!title.contains("pets")) {
            menuOpen = false;
            return;
        }
        if (!menuOpen) {
            menuOpen = true;
            menuLogged.clear();
            PetLog.line("MENU", "opened '" + title + "'");
        }

        boolean aktivesGefunden = false;
        for (Slot slot : screen.getMenu().slots) {
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;
            String feldText = clean(stack.getHoverName().getString());
            Matcher named = MENU_NAME.matcher(feldText);
            if (!named.matches()) continue;

            ItemLore lore = stack.get(DataComponents.LORE);
            // Jedes Pet im Menue merken, nicht nur das aktive: Wer spaeter ein anderes
            // ausruestet, haette sonst wieder kein Bild und muesste das Menue erneut
            // oeffnen - genau das soll das Merken ja ersparen.
            //
            // Unter seinem Schluessel: Zwei Pets gleichen Namens - eines mit Skin, oder mit anderem Item - sind zwei
            // Eintraege mit je eigenem Bild. Das Item steht in der Beschreibung des Feldes, das Skin-Zeichen im Namen
            String feldName = cleanName(named.group("name"));
            boolean feldSkin = hasSkinMark(displayName(named.group("name")));
            String feldItem = loreItem(lore);
            String feldKey = PetIdentity.resolve(PetIcons.knownKeys(), feldName, feldSkin, feldItem).key();
            PetIcons.remember(feldKey, stack);
            logMenuPet(slot.index, feldText, feldItem, feldKey, stack, lore);

            // Rueckfall fuer das Bild: Traegt ein Feld den Schluessel des getragenen Pets und
            // fehlt uns noch ein Bild, wird es genommen - auch ohne den Hinweis auf das
            // Absetzen in der Beschreibung. Ein Pet mit Skin sieht dort anders aus als
            // erwartet, und ein Kasten ohne Bild ist schlechter als eines vom richtigen
            // Pet mit womoeglich falschem Skin
            if (icon.isEmpty() && !key.isEmpty() && feldKey.equalsIgnoreCase(key)) {
                icon = stack.copy();
                PetIcons.remember(key, icon, true);
            }

            if (lore == null) continue;
            if (!aktivesGefunden && readActive(named, lore, stack, feldKey, feldSkin)) aktivesGefunden = true;
        }
    }

    /**
     * Das Item aus der Beschreibung eines Feldes im Pet-Menue ("Held Item: Textbook").
     * Leer, wenn keines drinsteht - das Menue ist dort vollstaendig; null nur ohne Beschreibung.
     */
    private static String loreItem(ItemLore lore) {
        if (lore == null) return null;
        for (Component zeile : lore.lines()) {
            Matcher h = HELD_ITEM.matcher(clean(zeile.getString()).trim());
            if (h.matches()) return h.group("item").trim();
        }
        return "";
    }

    /** Ein Pet des Menues ins Protokoll - je Oeffnen einmal, mit den Daten, die das Item selbst mitbringt */
    private static void logMenuPet(int slotIndex, String fieldText, String item, String petKey, ItemStack stack,
                                   ItemLore lore) {
        if (!PetLog.enabled()) return;
        boolean aktiv = false;
        if (lore != null) {
            for (Component zeile : lore.lines()) {
                if (clean(zeile.getString()).toLowerCase(Locale.ROOT).contains(ACTIVE_HINT)) aktiv = true;
            }
        }
        String info = petInfoOf(stack);
        // Ein Eintrag je Feld, und nochmal, wenn sich Erfahrung oder Aktiv-Zeichen aendern
        if (!menuLogged.add(slotIndex + "|" + fieldText + "|" + aktiv + "|" + info)) return;
        PetLog.line("MENU", "slot " + slotIndex + " '" + fieldText + "' item "
                + (item == null ? "unknown" : item.isEmpty() ? "none" : "'" + item + "'") + " key " + petKey
                + (aktiv ? " ACTIVE" : "") + " petInfo=" + (info.isEmpty() ? "(none on the item)" : info));
    }

    /**
     * Die Daten, die das Pet-Item selbst traegt (Schluessel "petInfo" in den eigenen Daten): Art, Seltenheit,
     * genaue Erfahrung, Item, Skin, Kennung. SkyHanni liest die Pets daraus; hier erst einmal nur ins Protokoll, damit
     * sich belegen laesst, was Hypixel wirklich mitschickt.
     */
    private static String petInfoOf(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return "";
        String info = data.copyTag().getStringOr("petInfo", "");
        return info.length() > 600 ? info.substring(0, 600) + "..." : info;
    }

    /** Liest ein Feld aus, wenn es das aktive Pet ist. Wahr, sobald es passt */
    private static boolean readActive(Matcher named, ItemLore lore, ItemStack stack, String feldKey, boolean feldSkin) {
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

        // Das Menue sagt, welches Pet draussen ist - sicherer als jede andere Quelle, solange es offen ist
        boolean wechsel = !feldKey.equalsIgnoreCase(key);
        if (wechsel) switchTo(feldKey, false);
        long jetzt = System.currentTimeMillis();
        assertedAt = jetzt;
        assertedKey = feldKey;
        if (wechsel) PetLog.line("ID", "menu says the pet out is " + feldKey);
        skin = feldSkin;
        name = cleanName(named.group("name"));
        shown = displayName(named.group("name"));
        level = parseInt(named.group("lvl"));
        if (!seltenheit.isEmpty() && !rarityFromTab) rarity = seltenheit;
        percent = prozent;
        xpHave = haben;
        xpNeed = noetig;
        atMax = maxErreicht || atMax;
        totalXp = gesamt;
        totalXpPet = key;
        heldItem = getragen;
        icon = stack.copy();
        // Damit der Kasten sein Bild auch nach einem Neustart hat, ohne dass jemand
        // erst wieder das Pet-Menue oeffnen muss
        com.shokiteufel.shokimod.util.PetIcons.remember(key, icon, true);
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
        // Die Erfahrung muss zu diesem Pet gehoeren. Wer im Sekundentakt wechselt, hat
        // sonst die Zahl des einen und die Tabelle des anderen in derselben Rechnung
        if (!key.equalsIgnoreCase(totalXpPet)) return;
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
            PetIcons.rememberOverflow(key, name, overflowLevel, overflowXp, combinedLevel());
        }
    }

    /** Die Farbcode-Stelle direkt vor dem Namen: f/a/9/5/6/d = COMMON bis MYTHIC */
    private static String rarityFromColour(String formatted, String shownName) {
        if (formatted == null || shownName.isEmpty()) return "";
        int at = formatted.indexOf(shownName);
        if (at < 2 || formatted.charAt(at - 2) != '§') return "";
        return switch (formatted.charAt(at - 1)) {
            case 'f' -> "COMMON";
            case 'a' -> "UNCOMMON";
            case '9' -> "RARE";
            case '5' -> "EPIC";
            case '6' -> "LEGENDARY";
            case 'd' -> "MYTHIC";
            default -> "";
        };
    }

    private static String clean(String text) {
        return COLOUR_CODE.matcher(text == null ? "" : text).replaceAll("").trim();
    }

    /** Der Name ohne was andere Mods davorgesetzt haben - fuer die Anzeige */
    private static String displayName(String text) {
        return FOREIGN_TAG.matcher(clean(text)).replaceFirst("").trim();
    }

    /** Derselbe Name ohne Skin-Zeichen - darueber wird zugeordnet */
    private static String cleanName(String text) {
        return SKIN_MARK.matcher(displayName(text)).replaceAll("").trim();
    }

    /**
     * Was hinter dem Namen steht, gehoert nicht dazu.
     *
     * Ein Pet mit Skin traegt bei Hypixel ein Zeichen dahinter: "Ammonite ✦". Unter
     * diesem Namen fand weder das gemerkte Bild noch der gemerkte Ueberschuss sein Pet
     * wieder - im Kasten stand es deshalb ohne Bild da.
     */
    private static final Pattern SKIN_MARK = Pattern.compile("[^\\p{L}\\p{N}]+$");

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
