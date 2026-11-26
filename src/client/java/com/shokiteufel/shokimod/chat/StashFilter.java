package com.shokiteufel.shokimod.chat;

import com.shokiteufel.shokimod.data.ModConfig.ChatFilterTypesCategory;
import com.shokiteufel.shokimod.util.EivFormat;

import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chat Filter Types > Stash Messages: die vier Zeilen der Stash-Warnung ("You have 226 materials stashed away!", "This totals 3 types", "CLICK HERE
 * to pick them up!") werden zu einer Zeile mit Klick: "You have 226 materials in stash, totalling 3 types. Click to pickup your stash!".
 *
 * Herkunft der Idee: SkyHanni (LGPL-2.1, {@code StashCompact}). Von dort stammen die Regeln: Die Zeilen werden gelesen und ausgeblendet, die kurze Zeile
 * kommt bei der Zeile mit dem Klick; sie faellt weg, wenn die Menge unter der Schwelle liegt oder derselbe Stand schon gemeldet wurde ("Hide Dupe
 * Warnings", je Welt neu); die Zeile "One or more items didn't fit ..." faellt auf Wunsch weg; nach der Zeile mit dem Klick faellt die naechste Zeile
 * weg, wenn sie leer ist. Die Umsetzung ist eigene Arbeit: Java, Muster fuer die Zeile ohne Farbcodes. Anders als bei SkyHanni gibt es kein nachtraegliches
 * Loeschen schon gezeigter Zeilen: Die beiden leeren Zeilen VOR der Warnung bleiben stehen (dafuer gibt es den Schalter "Empty").
 */
final class StashFilter {

    private static final Pattern COUNT = Pattern.compile(" *You have ([\\d,]+) (item|material)s? stashed away!.*");
    private static final Pattern TYPES = Pattern.compile(" *\\(This totals ([\\d,]+) types? of (item|material)s? stashed!\\).*");
    private static final Pattern PICKUP = Pattern.compile(" *>>> CLICK HERE to pick (?:them|it) up! <<<.*");
    private static final Pattern ADDED = Pattern.compile(
            "One or more (?:item|material)s? didn't fit in your inventory and were added to your (?:item|material) stash! Click here to pick them up!");

    /** Ein gemeldeter Stand: wie viele, und ob "item" oder "material"; dazu, wie viele Arten es sind */
    static final class Stand {
        final int count;
        final String type;
        Integer types;

        Stand(int count, String type) {
            this.count = count;
            this.type = type;
        }

        /** Gleich sind zwei Staende bei gleicher Menge und Art - die Zahl der Arten zaehlt nicht (wie bei SkyHanni) */
        boolean same(Stand other) {
            return other != null && count == other.count && type.equals(other.type);
        }
    }

    private final HashMap<String, Stand> current = new HashMap<>();
    private final HashMap<String, Stand> last = new HashMap<>();
    private String currentType = null;
    private boolean hideNextIfEmpty = false;

    /** Neue Welt: die Staende gelten nicht mehr (auf Wunsch, sonst bleibt "last" stehen) */
    void worldChanged(boolean forgetReported) {
        current.clear();
        currentType = null;
        hideNextIfEmpty = false;
        if (forgetReported) last.clear();
    }

    /** Eine Zeile; null heisst, der Filter hat dazu nichts zu sagen */
    ChatVerdict line(String line, ChatFilterTypesCategory c) {
        // Die Zeile nach der Zeile mit dem Klick: Ist sie leer, geht sie mit. Nur die naechste, nie eine spaetere
        if (hideNextIfEmpty) {
            hideNextIfEmpty = false;
            if (line.isBlank()) return ChatVerdict.HIDE;
        }
        // Drei der vier Zeilen enthalten "stash", die mit dem Klick ">>> CLICK HERE"
        if (line.indexOf("stash") < 0 && line.indexOf(">>> CLICK HERE") < 0) return null;

        Matcher m = COUNT.matcher(line);
        if (m.matches()) {
            Stand stand = new Stand(parse(m.group(1)), m.group(2));
            currentType = stand.type;
            current.put(stand.type, stand);
            return ChatVerdict.HIDE;
        }
        m = TYPES.matcher(line);
        if (m.matches()) {
            currentType = m.group(2);
            Stand stand = current.get(currentType);
            if (stand != null) stand.types = parse(m.group(1));
            return ChatVerdict.HIDE;
        }
        if (PICKUP.matcher(line).matches()) {
            hideNextIfEmpty = true;
            return pickupLine(c);
        }
        if (c.stashHideAdded && ADDED.matcher(line).matches()) {
            hideNextIfEmpty = true;
            return ChatVerdict.HIDE;
        }
        return null;
    }

    /** Die Zeile mit dem Klick: sie selbst geht, an ihre Stelle kommt (wenn der Stand es verdient) die kurze Zeile */
    private ChatVerdict pickupLine(ChatFilterTypesCategory c) {
        if (currentType == null) return ChatVerdict.HIDE;
        Stand stand = current.get(currentType);
        if (stand == null) return ChatVerdict.HIDE;
        if (stand.count <= c.stashHideLow) return ChatVerdict.HIDE;
        if (c.stashHideDupes && stand.same(last.get(currentType))) return ChatVerdict.HIDE;

        current.remove(currentType);
        last.put(currentType, stand);
        return ChatVerdict.replace(compactLine(stand, c));
    }

    /** Die kurze Zeile, in den Farben der Art (Items gelb, Materials tuerkis), mit Klick und Text beim Zeigen */
    static ChatVerdict.Line compactLine(Stand stand, ChatFilterTypesCategory c) {
        boolean item = stand.type.equals("item");
        String main = item ? "§e" : "§b";
        String accent = item ? "§6" : "§3";
        String extra = stand.types == null ? "" : ", " + main + "totalling " + accent + stand.types + " " + plural(stand.types, "type") + main;
        String action = c.stashUseViewStash ? "view" : "pickup";
        String text = main + "You have " + accent + EivFormat.shortFormat(stand.count) + " " + main + plural(stand.count, stand.type) + " in stash" + extra
                + ". " + main + "Click to " + accent + action + " " + main + "your stash!";
        String command = c.stashUseViewStash ? "/viewstash " + stand.type : "/pickupstash";
        return new ChatVerdict.Line(text, command, "§eClick to " + action + " your " + stand.type + " stash!");
    }

    private static String plural(int amount, String word) {
        return amount == 1 ? word : word + "s";
    }

    private static int parse(String digits) {
        try {
            return Integer.parseInt(digits.replace(",", ""));
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }
}
