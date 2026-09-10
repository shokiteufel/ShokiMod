package com.shokiteufel.shokimod.scanner;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

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

    /** Nach einem Weltwechsel gilt der alte Stand nicht mehr */
    public static void reset() {
        name = "";
        level = 0;
        rarity = "";
        percent = -1.0;
        xpHave = -1.0;
        xpNeed = -1.0;
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
        String neu = clean(m.group("name"));
        int stufe = parseInt(m.group("lvl"));
        if (neu.isEmpty() || stufe <= 0) return;
        // Ein anderes Pet heisst: der alte Fortschritt gehoert nicht mehr dazu
        if (!neu.equalsIgnoreCase(name)) {
            percent = -1.0;
            xpHave = -1.0;
            xpNeed = -1.0;
            rarity = "";
        }
        name = neu;
        level = stufe;
        seenAt = System.currentTimeMillis();
        from = "autopet";
    }

    /** Der Blick ins offene Pet-Menue: nur dort steht der Fortschritt */
    public static void tick(Minecraft client) {
        if (client == null || !(client.screen instanceof AbstractContainerScreen<?> screen)) return;
        long now = System.currentTimeMillis();
        if (now - lastMenuLook < MENU_GAP_MILLIS) return;
        lastMenuLook = now;

        String title = clean(screen.getTitle().getString()).toLowerCase(Locale.ROOT);
        if (!title.contains("pet")) return;

        for (Slot slot : screen.getMenu().slots) {
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;
            Matcher named = MENU_NAME.matcher(clean(stack.getHoverName().getString()));
            if (!named.matches()) continue;
            ItemLore lore = stack.get(DataComponents.LORE);
            if (lore == null) continue;
            if (readActive(named, lore)) return;  // das aktive Pet ist gefunden
        }
    }

    /** Liest ein Feld aus, wenn es das aktive Pet ist. Wahr, sobald es passt */
    private static boolean readActive(Matcher named, ItemLore lore) {
        boolean active = false;
        String seltenheit = "";
        double prozent = -1.0;
        double haben = -1.0;
        double noetig = -1.0;
        String vorige = "";

        for (Component zeile : lore.lines()) {
            String text = clean(zeile.getString()).trim();
            String klein = text.toLowerCase(Locale.ROOT);
            if (klein.contains(ACTIVE_HINT)) active = true;
            // Die Seltenheit steht als letzte fette Zeile: "LEGENDARY"
            if (text.matches("^[A-Z ]{4,}$")) seltenheit = text.trim();

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

        name = clean(named.group("name"));
        level = parseInt(named.group("lvl"));
        if (!seltenheit.isEmpty()) rarity = seltenheit;
        percent = prozent;
        xpHave = haben;
        xpNeed = noetig;
        seenAt = System.currentTimeMillis();
        from = "pet menu";
        return true;
    }

    private static String clean(String text) {
        return COLOUR_CODE.matcher(text == null ? "" : text).replaceAll("").trim();
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
